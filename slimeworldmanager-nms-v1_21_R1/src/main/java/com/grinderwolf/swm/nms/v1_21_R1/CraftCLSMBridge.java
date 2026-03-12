package com.grinderwolf.swm.nms.v1_21_R1;

import com.grinderwolf.swm.clsm.CLSMBridge;
import com.grinderwolf.swm.clsm.ClassModifier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CraftCLSMBridge implements CLSMBridge {

    private static final Logger LOGGER = LogManager.getLogger("SWM Chunk Loader");

    private final v1_21_R1SlimeNMS nmsInstance;

    @Override
    public Object getChunk(Object worldObject, int x, int z) {
        if (!(worldObject instanceof CustomWorldServer)) {
            return null;
        }

        CustomWorldServer world = (CustomWorldServer) worldObject;
        return world.getChunk(x, z);
    }

    @Override
    public boolean saveChunk(Object world, Object chunkAccess) {
        if (!(world instanceof CustomWorldServer)) {
            return false;
        }

        if (!(chunkAccess instanceof LevelChunk)) {
            return true;
        }

        LevelChunk chunk = (LevelChunk) chunkAccess;

        if (!chunk.isUnsaved()) {
            return true;
        }

        ((CustomWorldServer) world).saveChunk(chunk);
        chunk.setUnsaved(false);

        return true;
    }

    @Override
    public Object[] getDefaultWorlds() {
        ServerLevel defaultWorld = nmsInstance.getDefaultWorld();
        ServerLevel netherWorld = nmsInstance.getDefaultNetherWorld();
        ServerLevel endWorld = nmsInstance.getDefaultEndWorld();

        if (defaultWorld != null || netherWorld != null || endWorld != null) {
            return new ServerLevel[] { defaultWorld, netherWorld, endWorld };
        }

        return null;
    }

    @Override
    public boolean isCustomWorld(Object world) {
        return world instanceof CustomWorldServer;
    }

    @Override
    public boolean skipWorldAdd(Object world) {
        if (!isCustomWorld(world) || nmsInstance.isLoadingDefaultWorlds()) {
            return false;
        }

        CustomWorldServer worldServer = (CustomWorldServer) world;
        return !worldServer.isReady();
    }

    static void initialize(v1_21_R1SlimeNMS instance) {
        ClassModifier.setLoader(new CraftCLSMBridge(instance));
    }
}
