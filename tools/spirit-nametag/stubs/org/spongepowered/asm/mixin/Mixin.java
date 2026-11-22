package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
/** Compile-time stand-in: the real class comes with Fabric at runtime. */
@Retention(RetentionPolicy.CLASS) @Target(ElementType.TYPE)
public @interface Mixin { Class<?>[] value() default {}; String[] targets() default {}; int priority() default 1000; }
