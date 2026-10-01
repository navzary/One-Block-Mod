package net.nav.oneblock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Overwrite;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.gui.screen.world.WorldCreator.WorldType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.world.level.storage.LevelStorage.Session;
import java.util.function.IntConsumer;
import java.nio.file.Path;
import java.nio.file.Files;
import static net.minecraft.resource.ResourcePackSource.WORLD;
import static net.minecraft.resource.ResourceType.SERVER_DATA;

@Mixin(WorldCreator.class) abstract class OneBlock {
    private static final String NAME = "One Block",ZIP = NAME + ".zip",ID = "file/" + ZIP;
    @Shadow private net.minecraft.client.world.GeneratorOptionsHolder generatorOptionsHolder;
    @Shadow private WorldType worldType;
    @Shadow private java.util.List<WorldType> normalWorldTypes,extendedWorldTypes;
    @Shadow private boolean generateStructures;

    @Inject(method = "updateWorldTypeLists",at = @At("TAIL"))
    private void ob$upd(org.spongepowered.asm.mixin.injection.callback.CallbackInfo i) {
        var packs = generatorOptionsHolder.dataConfiguration().dataPacks();
        boolean enabled = packs.getEnabled().contains(ID);
        if (!enabled && !packs.getDisabled().contains(ID)) {return;}
        var type = new WorldType(RegistryEntry.of(generatorOptionsHolder.getCombinedRegistryManager().get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.DEFAULT)));
        normalWorldTypes.add(0,type);extendedWorldTypes.add(0,type);
        if (enabled) worldType = type;
    }
    @Inject(method = "<init>",at = @At("TAIL"))
    private void ob$init(org.spongepowered.asm.mixin.injection.callback.CallbackInfo i) {if (worldType.preset() instanceof RegistryEntry.Direct<?>) generateStructures = false;}

    @Redirect(method = "setWorldType",at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/screen/world/WorldCreator;worldType:Lnet/minecraft/client/gui/screen/world/WorldCreator$WorldType;",opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    private void ob$set(WorldCreator wc,WorldType type) {
        worldType = type;
        boolean ob = type.preset() instanceof RegistryEntry.Direct<?>;
        if (ob == generatorOptionsHolder.dataConfiguration().dataPacks().getEnabled().contains(ID)) {return;}
        generateStructures = !ob;
        if (MinecraftClient.getInstance().currentScreen instanceof IntConsumer screen) screen.accept(ob ? 1 : 0);
    }
    @Mixin(WorldType.class) static class WT {
        private static final Text ob$name = Text.literal(NAME);
        @Inject(method = "getName",at = @At("HEAD"),cancellable = true)
        private void ob$get(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Text> c) {if (((WorldType)(Object)this).preset() instanceof RegistryEntry.Direct<?>) c.setReturnValue(ob$name);}
    }
    @Mixin(CreateWorldScreen.class) static abstract class CWS implements IntConsumer {
        @Shadow private ResourcePackManager packManager;
        @Shadow protected abstract com.mojang.datafixers.util.Pair<Path,ResourcePackManager> getScannedPack(net.minecraft.resource.DataConfiguration config);
        @Shadow protected abstract void applyDataPacks(ResourcePackManager packs,boolean packScreen,java.util.function.Consumer set);
        private static final Path ob$pack = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("datapacks/" + ZIP);
        private static ResourcePackProvider ob$provider(net.minecraft.util.path.SymlinkFinder finder) {
            return out -> {try {
                if (!Files.isRegularFile(ob$pack) || Files.isSymbolicLink(ob$pack) && !finder.validate(ob$pack).isEmpty()) {return;}
                var profile = net.minecraft.resource.ResourcePackProfile.create(
                        new net.minecraft.resource.ResourcePackInfo(ID,Text.literal(ZIP),WORLD,java.util.Optional.empty()),
                        new net.minecraft.resource.ZipResourcePack.ZipBackedFactory(ob$pack),SERVER_DATA,
                        new net.minecraft.resource.ResourcePackPosition(false,net.minecraft.resource.ResourcePackProfile.InsertionPosition.TOP,false));
                if (profile != null) out.accept(profile);
            } catch (java.io.IOException ignored) {}
            };
        }
        @Redirect(method = "create(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/gui/screen/Screen;)V",at = @At(value = "NEW",
                target = "net/minecraft/resource/ResourcePackManager"))
        private static ResourcePackManager ob$manager(ResourcePackProvider[] providers,MinecraftClient client) {return new ResourcePackManager(providers[0],ob$provider(client.getSymlinkFinder()));}

        @Redirect(method = "getScannedPack",at = @At(value = "INVOKE",
                target = "Lnet/minecraft/resource/VanillaDataPackProvider;createManager(Ljava/nio/file/Path;Lnet/minecraft/util/path/SymlinkFinder;)Lnet/minecraft/resource/ResourcePackManager;"))
        private ResourcePackManager ob$m(Path path,net.minecraft.util.path.SymlinkFinder finder) {return new ResourcePackManager(
                new net.minecraft.resource.VanillaDataPackProvider(finder),ob$provider(finder),
                new net.minecraft.resource.FileResourcePackProvider(path,SERVER_DATA,WORLD,finder));}

        public void accept(int state) {
            if (packManager == null && getScannedPack(((CreateWorldScreen)(Object)this).getWorldCreator().getGeneratorOptionsHolder().dataConfiguration()) == null) {return;}
            if (state != 0 ? packManager.enable(ID) : packManager.disable(ID)) applyDataPacks(packManager,false,e -> {});
        }
        @Inject(method = "createSession",at = @At("RETURN"))
        private void ob$icon(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.Optional<Session>> i) {
            var session = i.getReturnValue().orElse(null);
            if (session == null || !(((CreateWorldScreen)(Object)this).getWorldCreator().getWorldType().preset() instanceof RegistryEntry.Direct<?>)) {return;}
            try (var zip = new java.util.zip.ZipFile(ob$pack.toFile())) {
                Files.copy(ob$pack,Files.createDirectories(session.getDirectory(net.minecraft.util.WorldSavePath.DATAPACKS)).resolve(ZIP));
                var icon = zip.getEntry("pack.png");
                if (icon != null) Files.copy(zip.getInputStream(icon),session.getIconFile().get());
            } catch (java.io.IOException ignored) {}
        }
    }
    @Mixin(net.minecraft.server.integrated.IntegratedServerLoader.class) static class ISL {
        @Overwrite public static void tryLoad(MinecraftClient c,CreateWorldScreen s,com.mojang.serialization.Lifecycle l,Runnable go,boolean b) {go.run();}
        @Overwrite private void showBackupPromptScreen(Session s,boolean b,Runnable go,Runnable c) {go.run();}
    }
    @Mixin(net.minecraft.client.world.ClientWorld.Properties.class) static class CWP {
        @Overwrite public double getSkyDarknessHeight(net.minecraft.world.HeightLimitView h) {return Double.NaN;}
        @Overwrite public float getHorizonShadingRatio() {return Float.NaN;}
    }
}