package git.spirit.nametag.mixin;

import git.spirit.nametag.SpiritNametag;
import net.minecraft.class_1309;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shows your own name tag while you look at yourself in F5 view.
 * Vanilla never draws the label of the camera entity; this makes LivingEntityRenderer.hasLabel say "yes" for it
 * (as long as the HUD is not hidden with F1).
 *
 * Names are Fabric intermediary names, so this file needs no mappings at build time:
 *   class_922 = LivingEntityRenderer, method_3921 = hasLabel, class_310 = MinecraftClient,
 *   class_1309 = LivingEntity. Every injector has require = 0, so a Minecraft version where the method looks different
 *   simply gets no name tag instead of a crash.
 */
@Mixin(targets = "net.minecraft.class_922")
public class OwnNameMixin {
    /** Minecraft 1.21.2 and newer: hasLabel(entity, squaredDistanceToCamera) */
    @Inject(method = "method_3921(Lnet/minecraft/class_1309;D)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void spirit$ownNameNew(class_1309 entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if(entity == class_310.method_1551().method_1560()) { SpiritNametag.firstHit("LivingEntityRenderer"); cir.setReturnValue(class_310.method_1498()); }
    }

    /** Minecraft 1.20 to 1.21.1: hasLabel(entity) */
    @Inject(method = "method_3921(Lnet/minecraft/class_1309;)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void spirit$ownNameOld(class_1309 entity, CallbackInfoReturnable<Boolean> cir) {
        if(entity == class_310.method_1551().method_1560()) { SpiritNametag.firstHit("LivingEntityRenderer"); cir.setReturnValue(class_310.method_1498()); }
    }
}
