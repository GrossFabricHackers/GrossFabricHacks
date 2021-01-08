package net.devtech.grossfabrichacks.transformer;

import net.devtech.grossfabrichacks.GrossFabricHacks;
import net.devtech.grossfabrichacks.transformer.asm.AsmClassTransformer;
import net.devtech.grossfabrichacks.transformer.asm.RawClassTransformer;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.HackedMixinTransformer;
import user11681.reflect.Classes;

/**
 * The API class for getting access to transforming any and all classes loaded by the KnotClassLoader (or whatever classloader happens to calls mixin)
 */
public class TransformerApi {
	/**
	 * manually load the class, causing it to inject itself into the class loading pipe.
	 */
	public static void manualLoad() {
		if (GrossFabricHacks.Common.mixinLoaded) {
			Classes.load(GrossFabricHacks.Common.originalClassLoader, "org.spongepowered.asm.mixin.transformer.HackedMixinTransformer");
		} else {
			GrossFabricHacks.Common.shouldHackMixin = true;
		}
	}

	/**
	 * listeners are called before mixins are applied, and gives you raw access to the class' bytecode, allowing you to fiddle with things ASM normally doens't let you.
	 */
	public static void registerPreMixinRawClassTransformer(RawClassTransformer transformer) {
		if (GrossFabricHacks.Common.preMixinRawClassTransformer == null) {
			GrossFabricHacks.Common.preMixinRawClassTransformer = transformer;
		} else {
			GrossFabricHacks.Common.preMixinRawClassTransformer = GrossFabricHacks.Common.preMixinRawClassTransformer.andThen(transformer);
		}
	}

	/**
	 * transformers are called before mixin application with the class' classnode
	 */
	public static void registerPreMixinAsmClassTransformer(AsmClassTransformer transformer) {
		if (GrossFabricHacks.Common.preMixinAsmClassTransformer == null) {
			GrossFabricHacks.Common.preMixinAsmClassTransformer = transformer;
			GrossFabricHacks.Common.shouldWrite = true;
		} else {
			GrossFabricHacks.Common.preMixinAsmClassTransformer = GrossFabricHacks.Common.preMixinAsmClassTransformer.andThen(transformer);
		}
	}

	/**
	 * these are the last transformers to be called, and are fed the output of the classwritten classnode after mixin and postmixinasmtransformers.
	 */
	public static void registerPostMixinRawClassTransformer(RawClassTransformer transformer) {
		if (GrossFabricHacks.Common.postMixinRawClassTransformer == null) {
			GrossFabricHacks.Common.postMixinRawClassTransformer = transformer;
			GrossFabricHacks.Common.shouldWrite = true;
		} else {
			GrossFabricHacks.Common.postMixinRawClassTransformer = GrossFabricHacks.Common.postMixinRawClassTransformer.andThen(transformer);
		}
	}

	/**
	 * transformer is called right after mixin application.
	 */
	public static void registerPostMixinAsmClassTransformer(AsmClassTransformer transformer) {
		if (GrossFabricHacks.Common.postMixinAsmClassTransformer == null) {
			GrossFabricHacks.Common.postMixinAsmClassTransformer = transformer;
			GrossFabricHacks.Common.shouldWrite = true;
		} else {
			GrossFabricHacks.Common.postMixinAsmClassTransformer = GrossFabricHacks.Common.postMixinAsmClassTransformer.andThen(transformer);
		}
	}

	public static byte[] transformClass(ClassNode node) {
		return HackedMixinTransformer.instance.transform(MixinEnvironment.getCurrentEnvironment(), node, null);
	}

	static {
		manualLoad();
	}
}
