package git.artdeell.mojo.browse;

/** What the Browse screen is looking at: each kind installs into its own folder of the instance. */
public enum BrowseKind {
    MODS("mod", "mods"),
    PACKS("resourcepack", "resourcepacks"),
    SHADERS("shader", "shaderpacks"),
    /** Modrinth has no worlds, they come from CurseForge (needs the user's API key) */
    WORLDS(null, "saves"),
    /** Not installed from a site: the list of servers in servers.dat */
    SERVERS(null, "");

    /** Modrinth project_type, null when Modrinth does not have this kind */
    public final String modrinthType;
    public final String folder;

    BrowseKind(String modrinthType, String folder) {
        this.modrinthType = modrinthType;
        this.folder = folder;
    }
}
