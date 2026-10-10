package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.acoustics.AcousticProfileKey;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Decode and authenticate in preparation; publish one immutable catalog on the client thread. */
final class CalibratedAssetResources implements PreparableReloadListener {
    private static final String DIRECTORY="acoustic_asset_profiles";
    private static final AssetCatalogPublication<Catalog> PUBLICATION=
            new AssetCatalogPublication<>(new Catalog(Map.of()),OptionalDouble.empty());
    record BindingKey(AcousticProfileKey key,String layer,ResourceLocation resource) { }
    record Catalog(Map<BindingKey,VerifiedAssetPcm> assets) {
        Catalog {
            assets=Map.copyOf(assets);
            AssetCatalogHeadroom.validate(assets.values(),assetVoiceCapacity());
        }
        VerifiedAssetPcm select(AcousticProfileKey key,String layer,ResourceLocation resource) { return assets.get(new BindingKey(key,layer,resource)); }
        AssetCatalogHeadroom.Budget headroom() {
            return AssetCatalogHeadroom.validate(assets.values(),assetVoiceCapacity());
        }
    }
    private static int assetVoiceCapacity() {
        return StreamingVoiceBudget.MAX_DRONES*StreamingVoiceBudget.LAYERS_PER_DRONE;
    }
    @Override public CompletableFuture<Void> reload(SharedState sharedState, Executor preparationExecutor, PreparationBarrier barrier, Executor applyExecutor) {
        ResourceManager manager = sharedState.resourceManager();
        return load(manager, preparationExecutor).thenCompose(barrier::wait).thenCompose(next -> apply(next, manager, applyExecutor));
    }
    static Catalog snapshot() { return PUBLICATION.snapshot().catalog(); }
    public ResourceLocation getResourceId() { return ResourceLocation.parse("fpvdrone:acoustic_asset_profiles"); }
    public CompletableFuture<Catalog> load(ResourceManager manager,Executor executor) {
        Catalog previous=snapshot();
        return CompletableFuture.supplyAsync(()-> {
            try { return prepare(manager); }
            catch (IOException | RuntimeException e) {
                FpvDronecraftMod.LOGGER.error("Rejected calibrated asset reload; retaining previous PCM and coefficient catalog",e);
                return previous;
            }
        },executor);
    }
    static Catalog prepare(ResourceManager manager) throws IOException {
        var resources=manager.listResources(DIRECTORY,id->id.getPath().endsWith(".json"));
        if (resources.size()>32) throw new IOException("calibrated asset profile count exceeds 32");
        List<ResourceLocation> ids=new ArrayList<>(resources.keySet()); ids.sort(Comparator.naturalOrder());
        Map<BindingKey,VerifiedAssetPcm> next=new HashMap<>(); Set<String> profileIds=new HashSet<>(); long decodedBytes=0;
        for (ResourceLocation id:ids) {
            AudioAssetProfile profile;
            try(var reader=resources.get(id).openAsReader()) { profile=AudioAssetProfileJsonDecoder.decode(reader); }
            String expected=id.getNamespace()+":"+id.getPath().substring((DIRECTORY+"/").length(),id.getPath().length()-5);
            if (!expected.equals(profile.id()) || !profileIds.add(profile.id())) throw new IOException("calibrated asset profile identity mismatch");
            profile.requireReleaseDeclarations();
            decodedBytes+=(long)profile.calibration().frames()*2;
            if (decodedBytes>64*1024*1024) throw new IOException("calibrated asset catalog PCM exceeds 64MiB");
            ResourceLocation audio=ResourceLocation.parse(profile.resource()); BindingKey key=new BindingKey(profile.key(),profile.layer(),audio);
            if (next.containsKey(key)) throw new IOException("duplicate calibrated asset binding");
            byte[] encoded;
            try(var stream=manager.open(audio)) { encoded=stream.readNBytes(CalibratedAssetDecoder.MAX_ENCODED_BYTES+1); }
            next.put(key,CalibratedAssetDecoder.decode(profile.calibration(),encoded));
        }
        return new Catalog(next);
    }
    public CompletableFuture<Void> apply(Catalog next,ResourceManager manager,Executor executor) {
        return CompletableFuture.runAsync(()-> {
            Objects.requireNonNull(next,"calibrated asset catalog");
            PUBLICATION.publish(next,next.headroom().fullScalePressurePa());
        },executor);
    }
}
