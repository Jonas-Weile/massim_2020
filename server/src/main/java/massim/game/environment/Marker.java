package massim.game.environment;

import massim.protocol.data.Position;
import massim.protocol.data.Thing;

/**
 * A simple marker marking a position.
 */
public class Marker extends Positionable {

    private Type type;

    public Marker(Position pos, Type type) {
        super(pos);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public Thing toPercept(Position relativeTo) {
        var pos = getPosition().toLocal(relativeTo);
        return new Thing(pos.x, pos.y, Thing.TYPE_MARKER, type.name);
    }

    public enum Type {
        CLEAR("clear");

        public final String name;

        Type(String name) {
            this.name = name;
        }
    }
}