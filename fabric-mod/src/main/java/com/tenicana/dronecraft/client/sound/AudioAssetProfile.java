package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticProfileKey;
import com.tenicana.dronecraft.acoustics.AcousticSourceProfile;
import java.net.URI;
import java.util.Objects;

/** Asset-specific declarations. These are never inferred from an order-model profile. */
record AudioAssetProfile(String id, AcousticProfileKey key, String layer, String resource,
                         AudioAssetCalibration calibration, AcousticSourceProfile.Validation validation,
                         Evidence evidence) {
    AudioAssetProfile {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || resource == null || !resource.matches("[a-z0-9_.-]+:sounds/[a-z0-9_./-]+\\.ogg")
                || resource.contains("..") || (!"MOTOR".equals(layer) && !"PROPELLER".equals(layer)))
            throw new IllegalArgumentException("invalid asset identity or layer");
        Objects.requireNonNull(key); Objects.requireNonNull(calibration);
        Objects.requireNonNull(validation); Objects.requireNonNull(evidence);
    }
    void requireReleaseDeclarations() {
        if (!validation.passesReleaseGate()) throw new IllegalArgumentException("asset holdout release gate failed");
        evidence.requireComplete();
    }
    record Evidence(URI source, String license, String motor, String propeller, String geometry,
                    String measurementChain, String backgroundConditions,
                    String rawPressureSha256, String synchronizedRpmSha256,
                    String trainingSha256, String holdoutSha256, String validationReportSha256) {
        Evidence { Objects.requireNonNull(source); if (!source.isAbsolute()) throw new IllegalArgumentException("absolute evidence URI required"); }
        void requireComplete() {
            for (String text : new String[]{license,motor,propeller,geometry,measurementChain,backgroundConditions})
                if (text == null || text.isBlank() || text.length()>4096) throw new IllegalArgumentException("missing asset measurement evidence");
            for (String hash : new String[]{rawPressureSha256,synchronizedRpmSha256,trainingSha256,holdoutSha256,validationReportSha256})
                AudioAssetCalibration.requireHash(hash);
            if (trainingSha256.equals(holdoutSha256)) throw new IllegalArgumentException("training and holdout cannot be identical");
        }
    }
}
