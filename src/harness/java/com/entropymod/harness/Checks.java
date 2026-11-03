package com.entropymod.harness;

import java.lang.reflect.Field;

/**
 * Minimal assertion plumbing for the headless checks. No test framework on
 * purpose -- this runs against the real Minecraft runtime classpath and the
 * fewer moving parts between it and the shipped classes, the better.
 */
final class Checks {

	private static int passed;
	private static int failed;
	private static String section = "(none)";

	static void section(String name) {
		section = name;
		System.out.println();
		System.out.println("== " + name);
	}

	static void check(boolean condition, String what) {
		if (condition) {
			passed++;
			System.out.println("  PASS  " + what);
		} else {
			failed++;
			System.out.println("  FAIL  [" + section + "] " + what);
		}
	}

	static void checkNear(double actual, double expected, double tolerance, String what) {
		boolean ok = Math.abs(actual - expected) <= tolerance;
		check(ok, what + "  (expected " + expected + ", got " + actual + ")");
	}

	/**
	 * Reads a constant by reflection rather than by referencing it.
	 *
	 * <p>This matters: {@code static final} primitives are inlined at the
	 * <em>caller's</em> compile time, so a harness that simply named
	 * {@code GreenThumbBehavior.MULTIPLIER} would be comparing its own compiled-in
	 * snapshot against itself and would pass no matter what the shipped class
	 * says. Reflection reads the field out of the loaded class file.
	 */
	static double constant(Class<?> owner, String name) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			return ((Number) field.get(null)).doubleValue();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("No constant " + owner.getSimpleName() + "." + name, e);
		}
	}

	/**
	 * Whether a constant is declared at all. Used to assert a retired constant has
	 * genuinely been removed rather than merely set to a neutral value -- a
	 * neutral value can be quietly re-wired, an absent field cannot.
	 */
	static boolean hasConstant(Class<?> owner, String name) {
		try {
			owner.getDeclaredField(name);
			return true;
		} catch (NoSuchFieldException e) {
			return false;
		}
	}

	/**
	 * Whether a method is declared at all, by name, ignoring its signature.
	 *
	 * <p>The counterpart to {@link #hasConstant} for a retired code path: it
	 * asserts a hook was genuinely deleted rather than left in place returning a
	 * neutral value for nobody. A method that still exists can be re-wired by a
	 * later session that does not know why it was emptied.
	 */
	static boolean hasMethod(Class<?> owner, String name) {
		for (java.lang.reflect.Method method : owner.getDeclaredMethods()) {
			if (method.getName().equals(name)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a compiled class's constant pool mentions a given name.
	 *
	 * <p>The tool for asserting <em>which</em> vanilla method a hook calls, when
	 * calling it for real would need a live entity the harness cannot build. Two
	 * methods that both return a boolean are indistinguishable by reflection;
	 * they are not indistinguishable in the bytecode, because the callee's name is
	 * a UTF8 entry in the caller's constant pool.
	 *
	 * <p>Deliberately crude -- it does not parse the class file, it scans it. That
	 * is enough for the question being asked ("does this reference
	 * {@code isCrouching} rather than {@code isShiftKeyDown}") and has no
	 * dependency on a bytecode library. It can only prove a reference is
	 * <em>present</em> or <em>absent</em>, not where it is used.
	 */
	static boolean classReferences(Class<?> owner, String name) {
		String resource = "/" + owner.getName().replace('.', '/') + ".class";
		try (java.io.InputStream in = owner.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("Could not read class file for " + owner.getName());
			}
			byte[] bytes = in.readAllBytes();
			// The constant pool stores names as modified UTF-8; for ASCII identifiers
			// that is byte-for-byte ISO-8859-1, which never fails to decode.
			String raw = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
			return raw.contains(name);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Could not read class file for " + owner.getName(), e);
		}
	}

	/**
	 * Whether a named method's bytecode pushes {@code null} and then a reference, i.e.
	 * contains a decoded {@code aconst_null} immediately followed by an {@code aload}.
	 *
	 * <h2>Why this exists, and why {@link #classReferences} is not enough</h2>
	 *
	 * <p>Several vanilla broadcast entry points -- {@code Level.levelEvent},
	 * {@code Level.playSound}, anything reaching
	 * {@code PlayerList.broadcast(Player except, ...)} -- take a <b>nullable player to
	 * EXCLUDE</b> as their first argument. Passing the affected player there is a
	 * plausible-looking mistake that compiles, applies, broadcasts, and is structurally
	 * guaranteed to deliver nothing to the one player the effect is for, while working
	 * perfectly for every bystander. The method name is identical either way, so
	 * {@code classReferences} cannot see the difference; so is the descriptor.
	 *
	 * <p>This decodes the Code attribute <b>instruction by instruction</b> rather than
	 * scanning bytes, which is the part that makes it a real check: {@code aconst_null}
	 * is opcode {@code 0x01}, a byte that occurs constantly inside other instructions'
	 * operands, so a raw scan would pass on almost any method. Decoding means the
	 * {@code 0x01} found is genuinely an instruction.
	 *
	 * <p>It proves the method pushes an explicit null before a reference argument. It
	 * does not prove <em>which</em> call that null belongs to -- for that, keep the
	 * method small enough that there is only one candidate, which is the shape these
	 * broadcast helpers should have anyway.
	 */
	static boolean methodPushesExplicitNull(Class<?> owner, String methodName) {
		byte[] code = codeOf(owner, methodName);
		int i = 0;
		while (i < code.length) {
			int op = code[i] & 0xFF;
			int length = instructionLength(code, i);
			if (length <= 0) {
				// An opcode this decoder does not model. Refuse to guess rather than
				// silently resynchronise on a misaligned offset and report nonsense.
				throw new IllegalStateException("Cannot decode opcode 0x" + Integer.toHexString(op)
						+ " at offset " + i + " in " + owner.getSimpleName() + "." + methodName);
			}
			if (op == 0x01 && i + length < code.length) {
				int next = code[i + length] & 0xFF;
				boolean nextIsAload = next == 0x19 || (next >= 0x2a && next <= 0x2d);
				if (nextIsAload) {
					return true;
				}
			}
			i += length;
		}
		return false;
	}

	/** The Code attribute's instruction array for a named method. */
	private static byte[] codeOf(Class<?> owner, String methodName) {
		java.io.DataInputStream in = new java.io.DataInputStream(
				new java.io.ByteArrayInputStream(classBytes(owner)));
		try {
			in.readInt();       // magic
			in.readUnsignedShort(); // minor
			in.readUnsignedShort(); // major
			String[] utf8 = readConstantPool(in);
			in.readUnsignedShort(); // access flags
			in.readUnsignedShort(); // this class
			in.readUnsignedShort(); // super class
			int interfaces = in.readUnsignedShort();
			in.skipBytes(interfaces * 2);
			skipMembers(in);    // fields
			int methods = in.readUnsignedShort();
			for (int m = 0; m < methods; m++) {
				in.readUnsignedShort(); // access flags
				String name = utf8[in.readUnsignedShort()];
				in.readUnsignedShort(); // descriptor
				int attributes = in.readUnsignedShort();
				for (int a = 0; a < attributes; a++) {
					String attributeName = utf8[in.readUnsignedShort()];
					int attributeLength = in.readInt();
					if (!name.equals(methodName) || !"Code".equals(attributeName)) {
						in.skipBytes(attributeLength);
						continue;
					}
					in.readUnsignedShort(); // max stack
					in.readUnsignedShort(); // max locals
					byte[] code = new byte[in.readInt()];
					in.readFully(code);
					return code;
				}
			}
			throw new IllegalStateException(
					"No Code attribute for " + owner.getSimpleName() + "." + methodName);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Could not parse " + owner.getName(), e);
		}
	}

	/** Constant pool, keeping only the UTF8 entries -- the rest is skipped by width. */
	private static String[] readConstantPool(java.io.DataInputStream in) throws java.io.IOException {
		int count = in.readUnsignedShort();
		String[] utf8 = new String[count];
		for (int i = 1; i < count; i++) {
			int tag = in.readUnsignedByte();
			switch (tag) {
				case 1 -> utf8[i] = in.readUTF();
				case 7, 8, 16, 19, 20 -> in.skipBytes(2);
				case 15 -> in.skipBytes(3);
				case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
				case 5, 6 -> {
					in.skipBytes(8);
					i++; // longs and doubles take two pool slots
				}
				default -> throw new IllegalStateException("Unknown constant pool tag " + tag);
			}
		}
		return utf8;
	}

	/** Skips a fields[] or methods[] table, attributes and all. */
	private static void skipMembers(java.io.DataInputStream in) throws java.io.IOException {
		int count = in.readUnsignedShort();
		for (int i = 0; i < count; i++) {
			in.skipBytes(6); // access flags, name index, descriptor index
			int attributes = in.readUnsignedShort();
			for (int a = 0; a < attributes; a++) {
				in.skipBytes(2);
				in.skipBytes(in.readInt());
			}
		}
	}

	/** Byte length of the instruction at {@code offset}, or -1 if unmodelled. */
	private static int instructionLength(byte[] code, int offset) {
		int op = code[offset] & 0xFF;
		if (op == 0xc4) { // wide
			int widened = code[offset + 1] & 0xFF;
			return widened == 0x84 ? 6 : 4; // wide iinc, else wide load/store
		}
		if (op == 0xaa || op == 0xab) { // tableswitch / lookupswitch
			int pad = 3 - (offset % 4);
			int base = offset + 1 + pad;
			if (op == 0xaa) {
				int low = readInt(code, base + 4);
				int high = readInt(code, base + 8);
				return (base + 12 + (high - low + 1) * 4) - offset;
			}
			int pairs = readInt(code, base + 4);
			return (base + 8 + pairs * 8) - offset;
		}
		return OPERAND_BYTES[op] < 0 ? -1 : OPERAND_BYTES[op] + 1;
	}

	private static int readInt(byte[] code, int offset) {
		return ((code[offset] & 0xFF) << 24) | ((code[offset + 1] & 0xFF) << 16)
				| ((code[offset + 2] & 0xFF) << 8) | (code[offset + 3] & 0xFF);
	}

	/** Operand byte count per opcode; -1 marks one this decoder refuses to guess at. */
	private static final int[] OPERAND_BYTES = buildOperandTable();

	private static int[] buildOperandTable() {
		int[] table = new int[256];
		java.util.Arrays.fill(table, -1);
		// 0x00-0x0f nop, consts; 0x1a-0x35 the _n loads and array loads; etc. Every
		// opcode with no operands.
		int[] zero = {
			0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d,
			0x0e, 0x0f, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25,
			0x26, 0x27, 0x28, 0x29, 0x2a, 0x2b, 0x2c, 0x2d, 0x2e, 0x2f, 0x30, 0x31, 0x32, 0x33,
			0x34, 0x35, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46,
			0x47, 0x48, 0x49, 0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x50, 0x51, 0x52, 0x53, 0x54,
			0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f, 0x60, 0x61, 0x62,
			0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70,
			0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x7b, 0x7c, 0x7d, 0x7e,
			0x7f, 0x80, 0x81, 0x82, 0x83, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d,
			0x8e, 0x8f, 0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0xac, 0xad, 0xae,
			0xaf, 0xb0, 0xb1, 0xbe, 0xbf, 0xc2, 0xc3,
		};
		for (int op : zero) {
			table[op] = 0;
		}
		// bipush, ldc, the indexed loads/stores, newarray, ret
		int[] one = {
			0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc,
		};
		for (int op : one) {
			table[op] = 1;
		}
		// sipush, ldc_w, ldc2_w, the field/method/type refs, the branches, iinc
		int[] two = {
			0x11, 0x13, 0x14, 0x84, 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2,
			0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb,
			0xbd, 0xc0, 0xc1, 0xc6, 0xc7,
		};
		for (int op : two) {
			table[op] = 2;
		}
		table[0xc5] = 3; // multianewarray
		table[0xb9] = 4; // invokeinterface
		table[0xba] = 4; // invokedynamic
		table[0xc8] = 4; // goto_w
		table[0xc9] = 4; // jsr_w
		return table;
	}

	private static byte[] classBytes(Class<?> owner) {
		String resource = "/" + owner.getName().replace('.', '/') + ".class";
		try (java.io.InputStream in = owner.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("Could not read class file for " + owner.getName());
			}
			return in.readAllBytes();
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Could not read class file for " + owner.getName(), e);
		}
	}

	static int summary() {
		System.out.println();
		System.out.println("---------------------------------------------");
		System.out.println(passed + " passed, " + failed + " failed");
		return failed == 0 ? 0 : 1;
	}

	private Checks() {}
}
