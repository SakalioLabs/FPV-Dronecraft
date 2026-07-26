package com.tenicana.dronecraft.client.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import com.tenicana.dronecraft.client.sound.DroneSoundManager;

import java.nio.file.Path;

public final class AcousticDiagnosticClientCommands {
	private static final String COMMAND = "fpvdrone-acoustics";
	private static final String DIAGNOSTIC_DIRECTORY = "acoustic-diagnostics";
	private static final String BUNDLE_PREFIX = "dda-production-v1-";
	private static final String AUDIO_LAB_PREFIX = "audio-lab-v1-";
	private static final String BACKEND_TIMELINE_PREFIX =
			"backend-timeline-v1-";

	private AcousticDiagnosticClientCommands() {
	}

	public static void initialize() {
		ClientCommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess) -> dispatcher.register(
						ClientCommandManager.literal(COMMAND)
								.then(ClientCommandManager.literal("export-dda")
										.executes(context -> {
											Path output = context.getSource()
													.getClient()
													.gameDirectory
													.toPath()
													.resolve(DIAGNOSTIC_DIRECTORY)
													.resolve(
															BUNDLE_PREFIX
																	+ System.currentTimeMillis()
																	+ ".bin"
													);
											Path normalized =
													DroneSoundManager
															.requestDdaDiagnosticExport(
																	output
															);
											context.getSource().sendFeedback(
													Component.literal(
															"DDA export requested. "
																	+ "Keep an audible drone active; "
																	+ "output: " + normalized
													)
											);
											return 1;
										}))
								.then(ClientCommandManager.literal("export-status")
										.executes(context -> {
											context.getSource().sendFeedback(
													Component.literal(
															DroneSoundManager
																	.ddaDiagnosticExportStatus()
													)
											);
											return 1;
										}))
								.then(ClientCommandManager.literal("backend-status")
										.executes(context -> {
											context.getSource().sendFeedback(
													Component.literal(
															DroneSoundManager
																	.acousticBackendRuntimeStatus()
													)
											);
											return 1;
										}))
								.then(ClientCommandManager.literal(
										"export-backend-timeline"
								).executes(context -> {
									Path output = context.getSource()
											.getClient()
											.gameDirectory
											.toPath()
											.resolve(DIAGNOSTIC_DIRECTORY)
											.resolve(
													BACKEND_TIMELINE_PREFIX
															+ System
																	.currentTimeMillis()
															+ ".json"
											);
									Path normalized = DroneSoundManager
											.exportAcousticBackendTimeline(
													output
											);
									context.getSource().sendFeedback(
											Component.literal(
													"Backend metadata timeline "
															+ "exported without "
															+ "audio: "
															+ normalized
											)
									);
									return 1;
								}))
								.then(ClientCommandManager.literal("audio-lab")
										.then(audioLabBackend("dry"))
										.then(audioLabBackend("java-fdn"))
										.then(audioLabBackend("openal-efx"))
										.then(ClientCommandManager.literal("status")
												.executes(context -> {
													context.getSource().sendFeedback(
															Component.literal(
																	DroneSoundManager
																			.audioLabDiagnosticStatus()
															)
													);
													return 1;
												})))
				)
		);
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource>
	audioLabBackend(String backend) {
		return ClientCommandManager.literal(backend)
				.then(ClientCommandManager.literal("control")
						.executes(context -> startAudioLab(
								context.getSource(),
								backend,
								false
						)))
				.then(ClientCommandManager.literal("reload")
						.executes(context -> startAudioLab(
								context.getSource(),
								backend,
								true
						)));
	}

	private static int startAudioLab(
			FabricClientCommandSource source,
			String backend,
			boolean reload
	) {
		String variant = reload ? "reload" : "control";
		Path output = source.getClient()
				.gameDirectory
				.toPath()
				.resolve(DIAGNOSTIC_DIRECTORY)
				.resolve(
						AUDIO_LAB_PREFIX
								+ backend + "-"
								+ variant + "-"
								+ System.currentTimeMillis()
								+ ".json"
				);
		Path normalized = DroneSoundManager.requestAudioLabDiagnostic(
				output,
				backend,
				reload
		);
		source.sendFeedback(Component.literal(
				"Audio lab started. External recording is not controlled "
						+ "by Minecraft; backend=" + backend
						+ ", variant=" + variant
						+ ", timeline=" + normalized
		));
		return 1;
	}
}
