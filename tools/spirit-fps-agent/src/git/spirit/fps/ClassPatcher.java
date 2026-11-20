package git.spirit.fps;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Tiny class-file patcher with no dependencies (so it works on Java 8 up to the newest JVM).
 * It inserts "invokestatic owner.name()V" plus a nop (4 bytes, so switch padding stays aligned) at the very start of
 * one method and fixes everything that holds code offsets: exception table, StackMapTable, line numbers and local
 * variable ranges. If the class holds anything it does not understand, it throws and the caller keeps the original.
 */
public final class ClassPatcher {
    private ClassPatcher() {}

    private static final int SHIFT = 4;

    public static byte[] insertStaticCall(byte[] cls, String method, String desc, String owner, String name, String callDesc) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(cls));
        ByteArrayOutputStream bos = new ByteArrayOutputStream(cls.length + 128);
        DataOutputStream out = new DataOutputStream(bos);

        out.writeInt(in.readInt());          // magic
        out.writeShort(in.readUnsignedShort()); // minor
        out.writeShort(in.readUnsignedShort()); // major
        int cpCount = in.readUnsignedShort();

        // Constant pool: copy entry by entry, remember the Utf8 strings (for names).
        ByteArrayOutputStream cpBytes = new ByteArrayOutputStream();
        DataOutputStream cpOut = new DataOutputStream(cpBytes);
        String[] utf8 = new String[cpCount];
        for(int i = 1; i < cpCount; i++) {
            int tag = in.readUnsignedByte();
            cpOut.writeByte(tag);
            switch(tag) {
                case 1: { // Utf8
                    byte[] raw = new byte[in.readUnsignedShort()];
                    in.readFully(raw);
                    cpOut.writeShort(raw.length);
                    cpOut.write(raw);
                    utf8[i] = modifiedUtf8ToString(raw);
                    break;
                }
                case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18: // 4 bytes
                    cpOut.writeInt(in.readInt()); break;
                case 5: case 6: // long, double take two slots
                    cpOut.writeLong(in.readLong()); i++; break;
                case 7: case 8: case 16: case 19: case 20: // 2 bytes
                    cpOut.writeShort(in.readUnsignedShort()); break;
                case 15: // MethodHandle
                    cpOut.writeByte(in.readUnsignedByte()); cpOut.writeShort(in.readUnsignedShort()); break;
                default:
                    throw new IOException("Unknown constant pool tag " + tag);
            }
        }

        // New entries: Utf8 owner, Class, Utf8 name, Utf8 desc, NameAndType, Methodref
        int base = cpCount;
        writeUtf8(cpOut, owner);                       // base
        cpOut.writeByte(7); cpOut.writeShort(base);    // base+1  Class
        writeUtf8(cpOut, name);                        // base+2
        writeUtf8(cpOut, callDesc);                    // base+3
        cpOut.writeByte(12); cpOut.writeShort(base + 2); cpOut.writeShort(base + 3); // base+4 NameAndType
        cpOut.writeByte(10); cpOut.writeShort(base + 1); cpOut.writeShort(base + 4); // base+5 Methodref
        int methodrefIndex = base + 5;
        int newCount = cpCount + 6;
        if(newCount > 0xFFFF) throw new IOException("constant pool full");

        out.writeShort(newCount);
        out.write(cpBytes.toByteArray());

        out.writeShort(in.readUnsignedShort()); // access
        out.writeShort(in.readUnsignedShort()); // this
        out.writeShort(in.readUnsignedShort()); // super
        int ifaces = in.readUnsignedShort();
        out.writeShort(ifaces);
        for(int i = 0; i < ifaces; i++) out.writeShort(in.readUnsignedShort());

        int fields = in.readUnsignedShort();
        out.writeShort(fields);
        for(int i = 0; i < fields; i++) {
            out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort()); out.writeShort(in.readUnsignedShort());
            copyAttributes(in, out);
        }

        int methods = in.readUnsignedShort();
        out.writeShort(methods);
        int patched = 0;
        for(int i = 0; i < methods; i++) {
            int acc = in.readUnsignedShort();
            int nameIdx = in.readUnsignedShort();
            int descIdx = in.readUnsignedShort();
            out.writeShort(acc); out.writeShort(nameIdx); out.writeShort(descIdx);
            boolean target = method.equals(utf8[nameIdx]) && desc.equals(utf8[descIdx]);
            int attrs = in.readUnsignedShort();
            out.writeShort(attrs);
            for(int a = 0; a < attrs; a++) {
                int an = in.readUnsignedShort();
                int len = in.readInt();
                byte[] info = new byte[len];
                in.readFully(info);
                if(target && "Code".equals(utf8[an])) {
                    info = patchCode(info, utf8, methodrefIndex);
                    patched++;
                }
                out.writeShort(an); out.writeInt(info.length); out.write(info);
            }
        }
        if(patched != 1) throw new IOException("target method not found (" + patched + ")");

        // class attributes: copy the rest as is
        byte[] rest = new byte[in.available()];
        in.readFully(rest);
        out.write(rest);
        out.flush();
        return bos.toByteArray();
    }

    private static byte[] patchCode(byte[] code, String[] utf8, int methodref) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(code));
        ByteArrayOutputStream bos = new ByteArrayOutputStream(code.length + 16);
        DataOutputStream out = new DataOutputStream(bos);
        int maxStack = in.readUnsignedShort();
        int maxLocals = in.readUnsignedShort();
        int codeLen = in.readInt();
        byte[] body = new byte[codeLen];
        in.readFully(body);
        if(codeLen + SHIFT >= 65535) throw new IOException("method too long");

        out.writeShort(maxStack);
        out.writeShort(maxLocals);
        out.writeInt(codeLen + SHIFT);
        out.writeByte(0xB8);              // invokestatic
        out.writeShort(methodref);
        out.writeByte(0x00);              // nop
        out.write(body);

        int excLen = in.readUnsignedShort();
        out.writeShort(excLen);
        for(int i = 0; i < excLen; i++) {
            out.writeShort(in.readUnsignedShort() + SHIFT);
            out.writeShort(in.readUnsignedShort() + SHIFT);
            out.writeShort(in.readUnsignedShort() + SHIFT);
            out.writeShort(in.readUnsignedShort());
        }

        int attrs = in.readUnsignedShort();
        out.writeShort(attrs);
        for(int a = 0; a < attrs; a++) {
            int an = in.readUnsignedShort();
            int len = in.readInt();
            byte[] info = new byte[len];
            in.readFully(info);
            String n = utf8[an];
            if("StackMapTable".equals(n)) info = patchStackMap(info);
            else if("LineNumberTable".equals(n)) info = patchLineNumbers(info);
            else if("LocalVariableTable".equals(n) || "LocalVariableTypeTable".equals(n)) info = patchLocalVars(info);
            else throw new IOException("unsupported Code attribute " + n);
            out.writeShort(an); out.writeInt(info.length); out.write(info);
        }
        return bos.toByteArray();
    }

    /** Only the first frame holds an absolute position (offset_delta); all others are relative to it. */
    private static byte[] patchStackMap(byte[] info) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(info));
        ByteArrayOutputStream bos = new ByteArrayOutputStream(info.length + 4);
        DataOutputStream out = new DataOutputStream(bos);
        int entries = in.readUnsignedShort();
        out.writeShort(entries);
        if(entries == 0) return bos.toByteArray();
        int type = in.readUnsignedByte();
        if(type <= 63) {                         // same_frame
            int d = type + SHIFT;
            if(d <= 63) out.writeByte(d);
            else { out.writeByte(251); out.writeShort(d); }
        } else if(type <= 127) {                 // same_locals_1_stack_item_frame
            int d = type - 64 + SHIFT;
            if(d <= 63) out.writeByte(64 + d);
            else { out.writeByte(247); out.writeShort(d); }
        } else if(type == 247 || (type >= 248 && type <= 251) || (type >= 252 && type <= 255)) {
            out.writeByte(type);
            out.writeShort(in.readUnsignedShort() + SHIFT);
        } else {
            throw new IOException("bad stack map frame type " + type);
        }
        byte[] rest = new byte[in.available()];
        in.readFully(rest);
        out.write(rest);
        return bos.toByteArray();
    }

    private static byte[] patchLineNumbers(byte[] info) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(info));
        ByteArrayOutputStream bos = new ByteArrayOutputStream(info.length);
        DataOutputStream out = new DataOutputStream(bos);
        int n = in.readUnsignedShort();
        out.writeShort(n);
        for(int i = 0; i < n; i++) {
            out.writeShort(in.readUnsignedShort() + SHIFT);
            out.writeShort(in.readUnsignedShort());
        }
        return bos.toByteArray();
    }

    private static byte[] patchLocalVars(byte[] info) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(info));
        ByteArrayOutputStream bos = new ByteArrayOutputStream(info.length);
        DataOutputStream out = new DataOutputStream(bos);
        int n = in.readUnsignedShort();
        out.writeShort(n);
        for(int i = 0; i < n; i++) {
            int start = in.readUnsignedShort();
            int length = in.readUnsignedShort();
            if(start == 0) { out.writeShort(0); out.writeShort(length + SHIFT); }
            else { out.writeShort(start + SHIFT); out.writeShort(length); }
            out.writeShort(in.readUnsignedShort());
            out.writeShort(in.readUnsignedShort());
            out.writeShort(in.readUnsignedShort());
        }
        return bos.toByteArray();
    }

    private static void copyAttributes(DataInputStream in, DataOutputStream out) throws IOException {
        int attrs = in.readUnsignedShort();
        out.writeShort(attrs);
        for(int a = 0; a < attrs; a++) {
            out.writeShort(in.readUnsignedShort());
            int len = in.readInt();
            byte[] info = new byte[len];
            in.readFully(info);
            out.writeInt(len);
            out.write(info);
        }
    }

    private static void writeUtf8(DataOutputStream out, String s) throws IOException {
        out.writeByte(1);
        out.writeUTF(s); // identical to modified UTF-8 for the plain names we write
    }

    /** Good enough for the names we compare with (method names and descriptors). */
    private static String modifiedUtf8ToString(byte[] raw) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream(raw.length + 2);
            DataOutputStream d = new DataOutputStream(b);
            d.writeShort(raw.length);
            d.write(raw);
            return new DataInputStream(new ByteArrayInputStream(b.toByteArray())).readUTF();
        } catch (IOException e) {
            return "";
        }
    }
}
