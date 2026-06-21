package com.tenicana.dronecraft.sim.flight;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlightModelRouter {
	private final Map<String, FlightModel> models;
	private FlightModel activeModel;

	public FlightModelRouter(List<FlightModel> models, String defaultModelId) {
		if (models == null || models.isEmpty()) {
			throw new IllegalArgumentException("at least one flight model is required");
		}
		Map<String, FlightModel> byId = new LinkedHashMap<>();
		for (FlightModel model : models) {
			if (model == null) {
				continue;
			}
			FlightModel previous = byId.putIfAbsent(model.id(), model);
			if (previous != null) {
				throw new IllegalArgumentException("duplicate flight model id: " + model.id());
			}
		}
		if (byId.isEmpty()) {
			throw new IllegalArgumentException("at least one non-null flight model is required");
		}
		this.models = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
		this.activeModel = modelOrThrow(defaultModelId == null ? byId.keySet().iterator().next() : defaultModelId);
	}

	public FlightModel activeModel() {
		return activeModel;
	}

	public FlightModel select(String modelId) {
		activeModel = modelOrThrow(modelId);
		return activeModel;
	}

	public boolean hasModel(String modelId) {
		return modelId != null && models.containsKey(modelId);
	}

	public List<String> modelIds() {
		return List.copyOf(models.keySet());
	}

	public void initialize(FlightModelInitializationContext context) {
		activeModel.initialize(context);
	}

	public void reset(FlightStateSnapshot state) {
		activeModel.reset(state);
	}

	public FlightStepResult step(FlightStepContext context) {
		return activeModel.step(context);
	}

	public FlightStateSnapshot snapshot() {
		return activeModel.snapshot();
	}

	public FlightModelDiagnostics diagnostics() {
		return activeModel.diagnostics();
	}

	private FlightModel modelOrThrow(String modelId) {
		FlightModel model = models.get(modelId);
		if (model == null) {
			throw new IllegalArgumentException("unknown flight model id: " + modelId);
		}
		return model;
	}
}
