/*
 * Steam 'n' Rails
 * Copyright (c) 2022-2024 The Railways Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.railwayteam.railways.mixin.conductor_possession;

import com.railwayteam.railways.content.conductor.ConductorEntity;
import com.railwayteam.railways.content.conductor.ConductorPossessionController;
import com.railwayteam.railways.content.conductor.ServerPlayerPossessionAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes sure the server does not move the player viewing a camera to the camera's position
 *
 * Updated for 1.21.1: In 1.21.1, the tick() method calls entity.isAlive() on the camera without
 * a null check. We need to ensure the camera is never null, so instead of canceling setCamera
 * entirely, we let it proceed but intercept absMoveTo to prevent position sync.
 */
@Mixin(value = ServerPlayer.class, priority = 1200)
public abstract class ServerPlayerMixin implements ServerPlayerPossessionAccess {
	@Shadow public abstract Entity getCamera();

	// Track possession state separately since camera field gets reset
	@org.spongepowered.asm.mixin.Unique
	private ConductorEntity railways$possessedConductor = null;

	@org.spongepowered.asm.mixin.Unique
	public ConductorEntity railways$getPossessedConductor() {
		return railways$possessedConductor;
	}

	@org.spongepowered.asm.mixin.Unique
	public void railways$setPossessedConductor(ConductorEntity conductor) {
		this.railways$possessedConductor = conductor;
	}

	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isAlive()Z"), require = 0)
	private boolean railways$nullGuardIsAlive(Entity entity) {
		if (entity == null) return false;
		return entity.isAlive();
	}

	@com.llamalad7.mixinextras.injector.v2.WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;absMoveTo(DDDFF)V"), require = 0)
	private boolean railways$shouldAbsMoveTo(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
		return !ConductorPossessionController.isPossessingConductor(player);
	}

	@Inject(method = "setCamera", at = @At("HEAD"), cancellable = true)
	private void railways$railways$setCamera(Entity entityToSpectate, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer)(Object) this;
		Entity currentCamera = this.getCamera();

		if (entityToSpectate instanceof ConductorEntity) {
			ci.cancel();
			return;
		}

		if (currentCamera instanceof ConductorEntity conductor) {
			if (entityToSpectate == self) {
				ci.cancel();
			} else {
				conductor.stopViewing(self);
			}
		}
	}
}
