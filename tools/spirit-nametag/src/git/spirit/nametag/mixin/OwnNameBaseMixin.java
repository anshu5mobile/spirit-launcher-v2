package git.spirit.nametag.mixin;

import git.spirit.nametag.SpiritNametag;
import net.minecraft.class_1297;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Same idea on the base EntityRenderer (class_897), in case a version does not override hasLabel in LivingEntityRenderer. */
@Mixin(targets = "net.minecraft.class_897")
public class OwnNameBaseMixin {
    @Inject(method = "method_3921(Lnet/minecraft/class_1297;D)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void spirit$ownNameBaseNew(class_1297 entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if(entity == class_310.method_1551().method_1560()) { SpiritNametag.firstHit("EntityRenderer"); cir.setReturnValue(class_310.method_1498()); }
    }

    @Inject(method = "method_3921(Lnet/minecraft/class_1297;)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void spirit$ownNameBaseOld(class_1297 entity, CallbackInfoReturnable<Boolean> cir) {
        if(entity == class_310.method_1551().method_1560()) { SpiritNametag.firstHit("EntityRenderer"); cir.setReturnValue(class_310.method_1498()); }
    }
}
