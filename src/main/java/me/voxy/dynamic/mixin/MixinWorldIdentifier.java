package me.voxy.dynamic.mixin;

import me.voxy.dynamic.access.IPerAreaWorldIdentifier;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Mixin(value = WorldIdentifier.class, remap = false)
public abstract class MixinWorldIdentifier implements IPerAreaWorldIdentifier {
    @Shadow public long biomeSeed;
    @Shadow public ResourceKey<Level> key;
    @Shadow public ResourceKey<DimensionType> dimension;

    @Unique
    private String voxy_dynamic$subId = "";

    @Override
    @Unique
    public void setSubId(String subId) {
        this.voxy_dynamic$subId = subId;
    }

    @Override
    @Unique
    public String getSubId() {
        return this.voxy_dynamic$subId;
    }

    @Overwrite
    public int hashCode() {
        return (int) getLongHash();
    }

    @Overwrite(remap = false)
    public long getLongHash() {
        long h = mixStafford13(registryKeyHashCode(key)) ^ mixStafford13(registryKeyHashCode(dimension)) ^ mixStafford13(biomeSeed);
        if (this.voxy_dynamic$subId != null && !this.voxy_dynamic$subId.isEmpty()) {
            h ^= mixStafford13(this.voxy_dynamic$subId.hashCode());
        }
        return h;
    }

    @Overwrite
    public boolean equals(Object obj) {
        if (obj instanceof WorldIdentifier other) {
            IPerAreaWorldIdentifier otherAccess = (IPerAreaWorldIdentifier) other;
            return this.getLongHash() == other.getLongHash()
                    && this.biomeSeed == other.biomeSeed
                    && this.key.equals(other.key)
                    && this.dimension.equals(other.dimension)
                    && this.voxy_dynamic$subId.equals(otherAccess.getSubId());
        }
        return false;
    }

    @Inject(method = "getWorldId", at = @At("HEAD"), cancellable = true)
    private void onGetWorldId(CallbackInfoReturnable<String> cir) {
        if (this.voxy_dynamic$subId != null && !this.voxy_dynamic$subId.isEmpty()) {
            String data = this.biomeSeed + this.key.toString() + this.voxy_dynamic$subId;
            try {
                cir.setReturnValue(bytesToHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8))).substring(0, 32));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Shadow private static long mixStafford13(long v) { return 0; }
    @Shadow private static long registryKeyHashCode(ResourceKey<?> v) { return 0; }

    @Unique
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
