package me.voxy.dynamic.mixin;

import me.voxy.dynamic.DynamicVoxyManager;
import me.voxy.dynamic.access.IPerAreaWorldIdentifier;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = Level.class, priority = 2000)
public abstract class MixinWorld {
    @Shadow(remap = false)
    private WorldIdentifier identifier;

    @Unique
    private String voxy_dynamic$lastArea = null;

    @Unique
    private WorldIdentifier voxy_dynamic$cachedAreaId = null;

    @Overwrite(remap = false)
    public WorldIdentifier voxy$getIdentifier() {
        if (DynamicVoxyManager.getCurrentAreaId() == null) {
            return this.identifier;
        }

        String areaId = DynamicVoxyManager.getCurrentAreaId();
        if (!areaId.equals(voxy_dynamic$lastArea)) {
            WorldIdentifier base = this.identifier;
            if (base == null) {
                return null;
            }
            WorldIdentifier newId = new WorldIdentifier(base.key, base.biomeSeed, base.dimension);
            ((IPerAreaWorldIdentifier) newId).setSubId(areaId);
            voxy_dynamic$lastArea = areaId;
            voxy_dynamic$cachedAreaId = newId;
        }
        return voxy_dynamic$cachedAreaId;
    }
}
