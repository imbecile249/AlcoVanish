package local.alcovanish;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

public final class MobHighlights {
	enum Filter {
		ALL("Все", 0xFFFFFF), FRIENDLY("Дружелюбные", 0x47DA88),
		HOSTILE("Враждебные", 0xFF6354), BOSSES("Боссы", 0x9647DF);

		final String label;
		final int color;
		Filter(String label, int color) { this.label = label; this.color = color; }
	}

	private MobHighlights() { }

	static boolean isBoss(Mob mob) {
		var type = mob.getType();
		return type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WITHER
				|| type == EntityTypes.WARDEN || type == EntityTypes.ELDER_GUARDIAN;
	}

	static boolean isHostile(Mob mob) {
		if (mob instanceof net.minecraft.world.entity.NeutralMob neutral && !isBoss(mob)) {
			return mob.isAggressive() || neutral.isAngry() || mob.getTarget() != null
					|| mob instanceof net.minecraft.world.entity.animal.golem.IronGolem golem && golem.getAttackAnimationTick() > 0
					|| mob instanceof net.minecraft.world.entity.monster.EnderMan enderman && enderman.isCreepy();
		}
		return isBoss(mob) || mob instanceof Enemy || !mob.getType().getCategory().isFriendly();
	}

	static boolean matches(Mob mob, Filter filter) {
		return switch (filter) {
			case ALL -> true;
			case FRIENDLY -> !isHostile(mob);
			case HOSTILE -> isHostile(mob);
			case BOSSES -> isBoss(mob);
		};
	}

	public static boolean shouldOutline(Mob mob, net.minecraft.world.entity.player.Player player) {
		var config = NearbyWorkshopClient.CONFIG;
		return config.mobHighlightEnabled && player != null && mob.isAlive() && !mob.isInvisibleTo(player)
				&& mob.distanceToSqr(player) <= config.mobHighlightRadius * config.mobHighlightRadius
				&& matches(mob, config.mobHighlightFilter);
	}

	public static int color(Mob mob) {
		return isBoss(mob) ? Filter.BOSSES.color : isHostile(mob) ? Filter.HOSTILE.color : Filter.FRIENDLY.color;
	}
}
