package com.tenicana.dronecraft.client.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.Commands;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.minecraft.client.Minecraft;

import net.minecraft.commands.CommandSourceStack;
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
		NeoForge.EVENT_BUS.addListener(
				(RegisterClientCommandsEvent event) -> event.getDispatcher().register(
						Commands.literal(COMMAND)
								.then(Commands.literal("export-dda")
										.executes(context -> {
											Path output = Minecraft.getInstance()
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
											context.getSource().sendSystemMessage(
													Component.literal(
															"DDA export requested. "
																	+ "Keep an audible drone active; "
																	+ "output: " + normalized
													)
											);
											return 1;
										}))
								.then(Commands.literal("export-status")
										.executes(context -> {
											context.getSource().sendSystemMessage(
													Component.literal(
															DroneSoundManager
																	.ddaDiagnosticExportStatus()
													)
											);
											return 1;
										}))
								.then(Commands.literal("backend-status")
										.executes(context -> {
											context.getSource().sendSystemMessage(
													Component.literal(
															DroneSoundManager
																	.acousticBackendRuntimeStatus()
													)
											);
											return 1;
										}))
								.then(Commands.literal(
										"export-backend-timeline"
								).executes(context -> {
									Path output = Minecraft.getInstance()
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
									context.getSource().sendSystemMessage(
											Component.literal(
													"Backend metadata timeline "
															+ "exported without "
															+ "audio: "
															+ normalized
											)
									);
									return 1;
								}))
								.then(Commands.literal("audio-lab")
										.then(audioLabBackend("dry"))
										.then(audioLabBackend("java-fdn"))
										.then(audioLabBackend("openal-efx"))
										.then(Commands.literal("status")
												.executes(context -> {
													context.getSource().sendSystemMessage(
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

	private static LiteralArgumentBuilder<CommandSourceStack>
	audioLabBackend(String backend) {
		return Commands.literal(backend)
				.then(Commands.literal("control")
						.executes(context -> startAudioLab(
								context.getSource(),
								backend,
								false
						)))
				.then(Commands.literal("reload")
						.executes(context -> startAudioLab(
								context.getSource(),
								backend,
								true
						)));
	}

	private static int startAudioLab(
			CommandSourceStack source,
			String backend,
			boolean reload
	) {
		String variant = reload ? "reload" : "control";
		Path output = Minecraft.getInstance()
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
		source.sendSystemMessage(Component.literal(
				"Audio lab started. External recording is not controlled "
						+ "by Minecraft; backend=" + backend
						+ ", variant=" + variant
						+ ", timeline=" + normalized
		));
		return 1;
	}
}
