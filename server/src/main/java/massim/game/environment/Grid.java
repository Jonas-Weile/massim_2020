package massim.game.environment;

import massim.game.Entity;
import massim.protocol.data.Position;
import massim.util.Log;
import massim.util.RNG;

import java.util.*;
import java.util.stream.Collectors;

public class Grid {

    public static final Set<String> DIRECTIONS = Set.of("n", "s", "e", "w");
    public static final Set<String> ROTATION_DIRECTIONS = Set.of("cw", "ccw");

    private int dimX;
    private int dimY;
    private int attachLimit;
    private Map<Position, Set<Positionable>> thingsMap;
    private Terrain[][] terrainMap;
    private List<Marker> markers = new ArrayList<>();

    public Grid(int dimX, int dimY, int attachLimit) {
        this.dimX = dimX;
        this.dimY = dimY;
        this.attachLimit = attachLimit;
        thingsMap = new HashMap<>();
        terrainMap = new Terrain[dimX][dimY];
        for (Terrain[] col : terrainMap) Arrays.fill(col, Terrain.EMPTY);
        for (int x = 0; x < dimX; x++) {
            terrainMap[x][0] = Terrain.OBSTACLE;
            terrainMap[x][dimY - 1] = Terrain.OBSTACLE;
        }
        for (int y = 0; y < dimY; y++) {
            terrainMap[0][y] = Terrain.OBSTACLE;
            terrainMap[dimX - 1][y] = Terrain.OBSTACLE;
        }
    }

    public int getDimX() {
        return dimX;
    }

    public int getDimY() {
        return dimY;
    }

    public Entity createEntity(Position xy, String agentName, String teamName) {
        var e = new Entity(xy, agentName, teamName);
        insertThing(e);
        return e;
    }

    public Block createBlock(Position xy, String type) {
        if(!isUnblocked(xy)) return null;
        var b = new Block(xy, type);
        insertThing(b);
        return b;
    }

    public void removeThing(Positionable a) {
        if (a == null) return;
        if (a instanceof Attachable) ((Attachable) a).detachAll();
        getThings(a.getPosition()).remove(a);
    }

    public Set<Positionable> getThings(Position pos) {
        return thingsMap.computeIfAbsent(pos, kPos -> new HashSet<>());
    }

    private boolean insertThing(Positionable thing) {
        if (outOfBounds(thing.getPosition())) return false;
        getThings(thing.getPosition()).add(thing);
        return true;
    }

    private boolean outOfBounds(Position pos) {
        return pos.x < 0 || pos.y < 0 || pos.x >= dimX || pos.y >= dimY;
    }

    private void move(Set<Positionable> things, Map<Positionable, Position> newPositions) {
        things.forEach(a -> getThings(a.getPosition()).remove(a));
        for (Positionable thing : things) {
            var newPos = newPositions.get(thing);
            thing.setPosition(newPos);
            insertThing(thing);
        }
    }

    /**
     * Moves an Attachable to a given position.
     * Only works if target is free and attachable has nothing attached.
     */
    public void moveWithoutAttachments(Attachable a, Position pos) {
        if(isUnblocked(pos) && a.getAttachments().isEmpty()) {
            removeThing(a);
            a.setPosition(pos);
            insertThing(a);
        }
    }

    public boolean attach(Attachable a1, Attachable a2) {
        if (a1 == null || a2 == null) return false;
        if (a1.getPosition().distanceTo(a2.getPosition()) != 1) return false;

        var attachments = a1.collectAllAttachments();
        attachments.addAll(a2.collectAllAttachments());
        if (attachments.size() > attachLimit) return false;

        a1.attach(a2);
        return true;
    }

    public boolean detach(Attachable a1, Attachable a2) {
        if (a1.getPosition().distanceTo(a2.getPosition()) != 1) return false;
        if (!a1.getAttachments().contains(a2)) return false;
        a1.detach(a2);
        return true;
    }

    public void print(){
        var sb = new StringBuilder(dimX * dimY * 3 + dimY);
        for (int row = 0; row < dimY; row++){
            for (int col = 0; col < dimX; col++){
                sb.append("[").append(getThings(Position.of(col, row)).size()).append("]");
            }
            sb.append("\n");
        }
        System.out.println(sb.toString());
    }

    /**
     * @return whether the movement succeeded
     */
    public boolean moveWithAttached(Attachable anchor, String direction, int distance) {
        var things = new HashSet<Positionable>(anchor.collectAllAttachments());
        var newPositions = canMove(things, direction, distance);
        if (newPositions == null) return false;
        move(things, newPositions);
        return true;
    }

    /**
     * @return whether the rotation succeeded
     */
    public boolean rotateWithAttached(Attachable anchor, boolean clockwise) {
        var newPositions = canRotate(anchor, clockwise);
        if (newPositions == null) return false;
        move(newPositions.keySet(), newPositions);
        return true;
    }

    /**
     * Checks if the anchor element and all attachments can rotate 90deg in the given direction.
     * Intermediate positions (the "diagonals") are also checked for all attachments.
     * @return a map from the element and all attachments to their new positions after rotation or null if anything is blocked
     */
    private Map<Positionable, Position> canRotate(Attachable anchor, boolean clockwise) {
        var attachments = new HashSet<Positionable>(anchor.collectAllAttachments());
        if(attachments.stream().anyMatch(a -> a != anchor && a instanceof Entity)) return null;
        var newPositions = new HashMap<Positionable, Position>();
        for (Positionable a : attachments) {
            var rotatedPosition = a.getPosition();
            int distance = rotatedPosition.distanceTo(anchor.getPosition());
            for (int rotations = 0; rotations < distance; rotations++) {
                rotatedPosition = rotatedPosition.rotatedOneStep(anchor.getPosition(), clockwise);
                if(!isUnblocked(rotatedPosition, attachments)) return null;
                if(!isInBounds(rotatedPosition)) return null;
            }
            newPositions.put(a, rotatedPosition);
        }
        return newPositions;
    }

    private Map<Positionable, Position> canMove(Set<Positionable> things, String direction, int distance) {
        var newPositions = new HashMap<Positionable, Position>();
        for (Positionable thing : things) {
            for (int i = 1; i <= distance; i++) {
                var newPos = thing.getPosition().moved(direction, i);
                if (!isInBounds(newPos)) return null;
                if(!isUnblocked(newPos, things)) return null;
            }
            newPositions.put(thing, thing.getPosition().moved(direction, distance));
        }
        return newPositions;
    }

    public Position findRandomFreePosition() {
        int x = RNG.nextInt(dimX);
        int y = RNG.nextInt(dimY);
        final int startX = x;
        final int startY = y;
        while (!isUnblocked(Position.of(x,y))) {
            if (++x >= dimX) {
                x = 0;
                if (++y >= dimY) y = 0;
            }
            if (x == startX && y == startY) {
                Log.log(Log.Level.ERROR, "No free position");
                return null;
            }
        }
        return Position.of(x, y);
    }

    public Position findRandomFreePosition(Position center, int maxDistance) {
        int x = center.x;
        int y = center.y;
        int dx = RNG.nextInt(maxDistance + 1);
        int dy = maxDistance - dx;
        x += RNG.nextDouble() < .5? dx : -dx;
        y += RNG.nextDouble() < .5? dy : -dy;
        return Position.of(x, y);
    }

    /**
     * @return true if the cell is in the grid and there is no other collidable and the terrain is not an obstacle.
     */
    public boolean isUnblocked(Position xy) {
        return isUnblocked(xy, Collections.emptySet());
    }

    private boolean isUnblocked(Position xy, Set<Positionable> excludedObjects) {
        if (outOfBounds(xy)) return false;
        if (terrainMap[xy.x][xy.y] == Terrain.OBSTACLE) return false;

        var blockingThings = getThings(xy).stream()
                .filter(t -> t instanceof Attachable && !excludedObjects.contains(t))
                .collect(Collectors.toCollection(HashSet::new));
        return blockingThings.isEmpty();
    }

    public void setTerrain(int x, int y, Terrain terrainType) {
        terrainMap[x][y] = terrainType;
    }

    public Terrain getTerrain(Position pos) {
        if (outOfBounds(pos)) return Terrain.EMPTY;
        return terrainMap[pos.x][pos.y];
    }

    public boolean isInBounds(Position p) {
        return p.x >= 0 && p.y >= 0 && p.x < dimX && p.y < dimY;
    }

    public void createMarker(Position position, Marker.Type type) {
        if (!isInBounds(position)) return;
        var marker = new Marker(position, type);
        markers.add(marker);
        insertThing(marker);
    }

    public void deleteMarkers() {
        markers.forEach(this::removeThing);
        markers.clear();
    }

    public Position getRandomPosition() {
        return Position.of(RNG.nextInt(dimX), RNG.nextInt(dimY));
    }
}