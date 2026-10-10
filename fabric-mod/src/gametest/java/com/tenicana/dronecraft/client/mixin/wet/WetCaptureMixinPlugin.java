package com.tenicana.dronecraft.client.mixin.wet;

import com.google.gson.JsonParser;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Disable allocating legacy mixer probes only for the declared callback experiment. */
public final class WetCaptureMixinPlugin implements IMixinConfigPlugin {
    private boolean productionCallback;
    @Override public void onLoad(String mixinPackage) {
        String protocol=System.getProperty("fpvdrone.acoustics.mainOutputProtocol");
        if(protocol==null)return;
        try {
            var value=JsonParser.parseString(Files.readString(Path.of(protocol))).getAsJsonObject();
            productionCallback=value.has("production_callback_experiment") && value.get("production_callback_experiment").getAsBoolean();
        } catch(Exception error) {throw new IllegalStateException("could not preflight callback mixer probes",error);}
    }
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        return !(productionCallback && mixin.endsWith(".WetMixerStagesMixin"));
    }
    @Override public String getRefMapperConfig(){return null;}
    @Override public void acceptTargets(Set<String> mine,Set<String> others) { }
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) { }
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) { }
}
