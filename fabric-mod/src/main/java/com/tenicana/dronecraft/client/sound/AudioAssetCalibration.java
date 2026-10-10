package com.tenicana.dronecraft.client.sound;

import java.util.List;

/** Numeric pressure reference at one metre and bounded response; not physical qualification. */
record AudioAssetCalibration(
        String encodedSha256, String decodedSha256, int sampleRate, int frames,
        int referenceStartFrame, int referenceFrameCount, double referenceDigitalRms,
        double referencePressureRmsPa, double fullScalePressurePa,
        double referenceRpm, double referenceAxisCosine,
        List<Double> rpmAnchors, List<Double> axisCosineAnchors, List<List<Double>> responseGainDb
) {
    static final int MAX_FRAMES = 48_000 * 30;
    AudioAssetCalibration {
        requireHash(encodedSha256); requireHash(decodedSha256);
        if (sampleRate != 48_000 || frames < 1 || frames > MAX_FRAMES
                || referenceStartFrame < 0 || referenceFrameCount < 1
                || (long) referenceStartFrame + referenceFrameCount > frames)
            throw new IllegalArgumentException("invalid mono 48000 Hz PCM reference window");
        positive(referenceDigitalRms); positive(referencePressureRmsPa); positive(fullScalePressurePa);
        if (referenceDigitalRms > 1) throw new IllegalArgumentException("digital RMS exceeds full scale");
        positive(referenceRpm);
        rpmAnchors = List.copyOf(rpmAnchors); axisCosineAnchors = List.copyOf(axisCosineAnchors);
        responseGainDb = responseGainDb.stream().map(List::copyOf).toList();
        increasing(rpmAnchors, "RPM"); increasing(axisCosineAnchors, "axis cosine");
        if (rpmAnchors.getFirst() < referenceRpm * .5 || rpmAnchors.getLast() > referenceRpm * 2
                || axisCosineAnchors.getFirst() != -1 || axisCosineAnchors.getLast() != 1
                || !rpmAnchors.contains(referenceRpm) || !axisCosineAnchors.contains(referenceAxisCosine))
            throw new IllegalArgumentException("response must contain reference, cover signed angles and fit native pitch range");
        if (responseGainDb.size() != rpmAnchors.size()) throw new IllegalArgumentException("RPM response shape");
        double baseDb = 20 * Math.log10(referencePressureRmsPa / (referenceDigitalRms * fullScalePressurePa));
        for (var row : responseGainDb) {
            if (row.size() != axisCosineAnchors.size()) throw new IllegalArgumentException("angle response shape");
            for (double gain : row) if (!Double.isFinite(gain) || !Double.isFinite(baseDb)
                    || baseDb + gain < -120 - 1e-10 || baseDb + gain > 24 + 1e-10)
                throw new IllegalArgumentException("total asset gain outside [-120,24] dB");
        }
        if (responseGainDb.get(rpmAnchors.indexOf(referenceRpm))
                .get(axisCosineAnchors.indexOf(referenceAxisCosine)) != 0)
            throw new IllegalArgumentException("reference response gain must be zero");
    }
    double referenceAmplitude() { return referencePressureRmsPa / (referenceDigitalRms * fullScalePressurePa); }
    double maximumAmplitude() {
        return referenceAmplitude() * Math.pow(10, responseGainDb.stream().flatMap(List::stream)
                .mapToDouble(Double::doubleValue).max().orElseThrow() / 20);
    }
    boolean contains(double rpm, double cosine) {
        return Double.isFinite(rpm) && Double.isFinite(cosine) && rpm >= rpmAnchors.getFirst()
                && rpm <= rpmAnchors.getLast() && cosine >= -1 && cosine <= 1;
    }
    double amplitude(double rpm, double cosine) {
        if (!contains(rpm, cosine)) throw new IllegalArgumentException("outside asset response domain");
        int r = interval(rpmAnchors, rpm), a = interval(axisCosineAnchors, cosine);
        double rf = Math.log(rpm / rpmAnchors.get(r)) / Math.log(rpmAnchors.get(r+1) / rpmAnchors.get(r));
        double af = (cosine-axisCosineAnchors.get(a)) / (axisCosineAnchors.get(a+1)-axisCosineAnchors.get(a));
        double lower = lerp(responseGainDb.get(r).get(a), responseGainDb.get(r).get(a+1), af);
        double upper = lerp(responseGainDb.get(r+1).get(a), responseGainDb.get(r+1).get(a+1), af);
        return referenceAmplitude() * Math.pow(10, lerp(lower, upper, rf) / 20);
    }
    private static int interval(List<Double> anchors, double value) {
        int i=0; while (i < anchors.size()-2 && value > anchors.get(i+1)) i++; return i;
    }
    private static double lerp(double a, double b, double f) { return a+(b-a)*f; }
    private static void increasing(List<Double> values, String name) {
        if (values.size() < 2 || values.size() > 64) throw new IllegalArgumentException(name+" anchors size");
        double previous = -Double.MAX_VALUE;
        for (double value : values) { if (!Double.isFinite(value) || value <= previous)
            throw new IllegalArgumentException(name+" anchors order"); previous=value; }
    }
    static void requireHash(String hash) { if (hash == null || !hash.matches("[0-9a-f]{64}"))
        throw new IllegalArgumentException("SHA256 must be lowercase hex"); }
    private static void positive(double value) { if (!Double.isFinite(value) || value <= 0)
        throw new IllegalArgumentException("reference must be finite and positive"); }
}
