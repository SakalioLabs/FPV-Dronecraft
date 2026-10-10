package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.tenicana.dronecraft.acoustics.AcousticProfileKey;
import com.tenicana.dronecraft.acoustics.AcousticSourceProfile;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

/** Strict independent asset schema; a source-model calibration is not accepted here. */
final class AudioAssetProfileJsonDecoder {
    private AudioAssetProfileJsonDecoder() { }
    static AudioAssetProfile decode(Reader source) {
        try {
            JsonReader reader=new JsonReader(source); reader.setLenient(false);
            JsonObject root=object(read(reader));
            if (reader.peek()!=JsonToken.END_DOCUMENT) throw new IllegalArgumentException("trailing JSON");
            fields(root,"schema_version","id","key","layer","resource","calibration","validation","evidence");
            if (integer(root,"schema_version")!=1) throw new IllegalArgumentException("unsupported asset schema");
            JsonObject k=object(root.get("key"));
            fields(k,"airframe_preset","rotor_count","blade_count","rotor_radius_mm","motor_pole_pairs");
            JsonObject c=object(root.get("calibration"));
            fields(c,"encoded_sha256","decoded_sha256","sample_rate","frames","reference_start_frame",
                    "reference_frame_count","reference_digital_rms","reference_pressure_rms_pa","full_scale_pressure_pa",
                    "reference_distance_m","reference_rpm","reference_axis_cosine","rpm_anchors","axis_cosine_anchors","response_gain_db");
            if (number(c,"reference_distance_m")!=1) throw new IllegalArgumentException("asset pressure reference must be at one metre");
            List<List<Double>> matrix=new ArrayList<>();
            for (JsonElement row:array(c.get("response_gain_db"))) matrix.add(numbers(row));
            JsonObject v=object(root.get("validation"));
            fields(v,"evaluated","unseen_rpm_samples","unseen_angle_samples","order_spectrum_samples",
                    "maximum_unseen_rpm_error_db","maximum_unseen_angle_error_db","maximum_order_spectrum_error_db");
            JsonObject e=object(root.get("evidence"));
            fields(e,"source","license","motor","propeller","geometry","measurement_chain","background_conditions",
                    "raw_pressure_sha256","synchronized_rpm_sha256","training_sha256","holdout_sha256","validation_report_sha256");
            return new AudioAssetProfile(text(root,"id"),new AcousticProfileKey(text(k,"airframe_preset"),
                    integer(k,"rotor_count"),integer(k,"blade_count"),integer(k,"rotor_radius_mm"),integer(k,"motor_pole_pairs")),
                    text(root,"layer"),text(root,"resource"),new AudioAssetCalibration(text(c,"encoded_sha256"),text(c,"decoded_sha256"),
                    integer(c,"sample_rate"),integer(c,"frames"),integer(c,"reference_start_frame"),integer(c,"reference_frame_count"),
                    number(c,"reference_digital_rms"),number(c,"reference_pressure_rms_pa"),number(c,"full_scale_pressure_pa"),
                    number(c,"reference_rpm"),number(c,"reference_axis_cosine"),numbers(c.get("rpm_anchors")),numbers(c.get("axis_cosine_anchors")),matrix),
                    new AcousticSourceProfile.Validation(bool(v,"evaluated"),integer(v,"unseen_rpm_samples"),integer(v,"unseen_angle_samples"),
                    integer(v,"order_spectrum_samples"),number(v,"maximum_unseen_rpm_error_db"),number(v,"maximum_unseen_angle_error_db"),
                    number(v,"maximum_order_spectrum_error_db")),new AudioAssetProfile.Evidence(URI.create(text(e,"source")),text(e,"license"),
                    text(e,"motor"),text(e,"propeller"),text(e,"geometry"),text(e,"measurement_chain"),text(e,"background_conditions"),
                    text(e,"raw_pressure_sha256"),text(e,"synchronized_rpm_sha256"),text(e,"training_sha256"),text(e,"holdout_sha256"),text(e,"validation_report_sha256")));
        } catch (IOException e) { throw new IllegalArgumentException("invalid asset JSON",e); }
    }
    private static JsonElement read(JsonReader r) throws IOException {
        return switch (r.peek()) {
            case BEGIN_OBJECT -> {
                var o=new JsonObject(); r.beginObject();
                while (r.hasNext()) { String name=r.nextName(); if (o.has(name)) throw new IllegalArgumentException("duplicate field: "+name); o.add(name,read(r)); }
                r.endObject(); yield o;
            }
            case BEGIN_ARRAY -> { var a=new JsonArray(); r.beginArray(); while(r.hasNext()) a.add(read(r)); r.endArray(); yield a; }
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            default -> throw new IllegalArgumentException("unexpected JSON token "+r.peek());
        };
    }
    private static JsonObject object(JsonElement e) { if (e==null || !e.isJsonObject()) throw new IllegalArgumentException("object required"); return e.getAsJsonObject(); }
    private static JsonArray array(JsonElement e) { if (e==null || !e.isJsonArray()) throw new IllegalArgumentException("array required"); return e.getAsJsonArray(); }
    private static void fields(JsonObject o,String... names) { if (!o.keySet().equals(Set.of(names))) throw new IllegalArgumentException("missing or unknown fields: "+o.keySet()); }
    private static String text(JsonObject o,String k) { var p=o.get(k); if (p==null || !p.isJsonPrimitive() || !p.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(k+" string required"); return p.getAsString(); }
    private static double number(JsonObject o,String k) { return number(o.get(k)); }
    private static double number(JsonElement e) { if (e==null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("number required"); double v=e.getAsDouble(); if (!Double.isFinite(v)) throw new IllegalArgumentException("finite number required"); return v; }
    private static int integer(JsonObject o,String k) {
        number(o,k);
        try { return new BigDecimal(o.get(k).getAsString()).intValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException(k+" exact integer required",e); }
    }
    private static boolean bool(JsonObject o,String k) { var e=o.get(k); if (e==null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(k+" boolean required"); return e.getAsBoolean(); }
    private static List<Double> numbers(JsonElement e) { List<Double> result=new ArrayList<>(); for(var item:array(e)) result.add(number(item)); return result; }
}
