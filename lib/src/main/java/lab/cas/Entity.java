package lab.cas;

public record Entity(long id, String name, int version) {

    public Entity withNextVersion() {
        return new Entity(id, name, version+1);
    }

    public Entity withName(String newName) {
        return new Entity(id, newName, version);
    }
}
