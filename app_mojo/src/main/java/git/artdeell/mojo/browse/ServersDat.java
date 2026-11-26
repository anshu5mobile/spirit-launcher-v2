package git.artdeell.mojo.browse;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds a server to the game's servers.dat (an uncompressed NBT file) without touching the servers already in it.
 * Only what Minecraft's own list needs is written: name and ip. The old file is kept as servers.dat_old, like the game does.
 */
public final class ServersDat {
    private ServersDat() {}

    private static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7,
            STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    /** A tag with its type, so everything read from the file is written back unchanged */
    private static final class Tag {
        final int type;
        final Object value;
        Tag(int type, Object value) { this.type = type; this.value = value; }
    }

    private static final class ListValue {
        final int elementType;
        final List<Object> items;
        ListValue(int elementType, List<Object> items) { this.elementType = elementType; this.items = items; }
    }

    private static Map<String, Tag> load(File file) throws IOException {
        if(!file.isFile() || file.length() == 0) return new LinkedHashMap<>();
        try(DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if(in.readByte() != COMPOUND) throw new IOException("servers.dat is not valid");
            in.readUTF();
            return readCompound(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static ListValue serversOf(Map<String, Tag> root) throws IOException {
        Tag serversTag = root.get("servers");
        if(serversTag != null && serversTag.type == LIST) {
            ListValue servers = (ListValue) serversTag.value;
            if(servers.elementType != COMPOUND && !servers.items.isEmpty()) throw new IOException("servers.dat is not valid");
            return servers;
        }
        return new ListValue(COMPOUND, new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    private static String stringOf(Object entry, String key) {
        Tag t = ((Map<String, Tag>) entry).get(key);
        return t != null && t.type == STRING ? (String) t.value : "";
    }

    private static void save(File gameDir, Map<String, Tag> root, ListValue servers) throws IOException {
        File file = new File(gameDir, "servers.dat");
        root.put("servers", new Tag(LIST, new ListValue(COMPOUND, servers.items)));
        if(file.isFile()) {
            File old = new File(gameDir, "servers.dat_old");
            //noinspection ResultOfMethodCallIgnored
            old.delete();
            //noinspection ResultOfMethodCallIgnored
            file.renameTo(old);
        }
        try(DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            writeCompound(out, root);
        }
    }

    /** @return the saved servers as {name, ip} pairs, in the order the game shows them */
    public static List<String[]> list(File gameDir) throws IOException {
        List<String[]> out = new ArrayList<>();
        ListValue servers = serversOf(load(new File(gameDir, "servers.dat")));
        for(Object o : servers.items) out.add(new String[]{stringOf(o, "name"), stringOf(o, "ip")});
        return out;
    }

    /** @return true when the server was added, false when the same address was already in the list */
    public static boolean add(File gameDir, String name, String ip) throws IOException {
        Map<String, Tag> root = load(new File(gameDir, "servers.dat"));
        ListValue servers = serversOf(root);
        for(Object o : servers.items) if(stringOf(o, "ip").equalsIgnoreCase(ip)) return false;
        Map<String, Tag> entry = new LinkedHashMap<>();
        entry.put("name", new Tag(STRING, name));
        entry.put("ip", new Tag(STRING, ip));
        servers.items.add(entry);
        save(gameDir, root, servers);
        return true;
    }

    /** Removes the server with this address; other servers (and everything else the game stored) stay as they were */
    public static boolean remove(File gameDir, String ip) throws IOException {
        Map<String, Tag> root = load(new File(gameDir, "servers.dat"));
        ListValue servers = serversOf(root);
        boolean removed = false;
        for(java.util.Iterator<Object> it = servers.items.iterator(); it.hasNext(); ) {
            if(stringOf(it.next(), "ip").equalsIgnoreCase(ip)) { it.remove(); removed = true; }
        }
        if(removed) save(gameDir, root, servers);
        return removed;
    }

    // ---------------------------------------------------------------- reading

    private static Map<String, Tag> readCompound(DataInputStream in) throws IOException {
        Map<String, Tag> map = new LinkedHashMap<>();
        while(true) {
            int type = in.readByte();
            if(type == END) return map;
            String name = in.readUTF();
            map.put(name, new Tag(type, readValue(in, type)));
        }
    }

    private static Object readValue(DataInputStream in, int type) throws IOException {
        switch(type) {
            case BYTE: return in.readByte();
            case SHORT: return in.readShort();
            case INT: return in.readInt();
            case LONG: return in.readLong();
            case FLOAT: return in.readFloat();
            case DOUBLE: return in.readDouble();
            case BYTE_ARRAY: { byte[] b = new byte[in.readInt()]; in.readFully(b); return b; }
            case STRING: return in.readUTF();
            case LIST: {
                int elementType = in.readByte();
                int size = in.readInt();
                List<Object> items = new ArrayList<>();
                for(int i = 0; i < size; i++) items.add(elementType == COMPOUND ? readCompound(in) : readValue(in, elementType));
                return new ListValue(elementType, items);
            }
            case COMPOUND: return readCompound(in);
            case INT_ARRAY: { int[] a = new int[in.readInt()]; for(int i = 0; i < a.length; i++) a[i] = in.readInt(); return a; }
            case LONG_ARRAY: { long[] a = new long[in.readInt()]; for(int i = 0; i < a.length; i++) a[i] = in.readLong(); return a; }
            default: throw new IOException("unknown tag " + type);
        }
    }

    // ---------------------------------------------------------------- writing

    @SuppressWarnings("unchecked")
    private static void writeCompound(DataOutputStream out, Map<String, Tag> map) throws IOException {
        for(Map.Entry<String, Tag> e : map.entrySet()) {
            out.writeByte(e.getValue().type);
            out.writeUTF(e.getKey());
            writeValue(out, e.getValue().type, e.getValue().value);
        }
        out.writeByte(END);
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(DataOutputStream out, int type, Object v) throws IOException {
        switch(type) {
            case BYTE: out.writeByte((Byte) v); break;
            case SHORT: out.writeShort((Short) v); break;
            case INT: out.writeInt((Integer) v); break;
            case LONG: out.writeLong((Long) v); break;
            case FLOAT: out.writeFloat((Float) v); break;
            case DOUBLE: out.writeDouble((Double) v); break;
            case BYTE_ARRAY: { byte[] b = (byte[]) v; out.writeInt(b.length); out.write(b); break; }
            case STRING: out.writeUTF((String) v); break;
            case LIST: {
                ListValue l = (ListValue) v;
                out.writeByte(l.items.isEmpty() ? COMPOUND : l.elementType);
                out.writeInt(l.items.size());
                for(Object item : l.items) {
                    if(l.elementType == COMPOUND) writeCompound(out, (Map<String, Tag>) item);
                    else writeValue(out, l.elementType, item);
                }
                break;
            }
            case COMPOUND: writeCompound(out, (Map<String, Tag>) v); break;
            case INT_ARRAY: { int[] a = (int[]) v; out.writeInt(a.length); for(int x : a) out.writeInt(x); break; }
            case LONG_ARRAY: { long[] a = (long[]) v; out.writeInt(a.length); for(long x : a) out.writeLong(x); break; }
            default: throw new IOException("unknown tag " + type);
        }
    }
}
