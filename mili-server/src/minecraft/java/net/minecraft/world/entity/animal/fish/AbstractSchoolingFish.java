package net.minecraft.world.entity.animal.fish;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FollowFlockLeaderGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

public abstract class AbstractSchoolingFish extends AbstractFish {
    public @Nullable AbstractSchoolingFish leader;
    public int schoolSize = 1;

    public AbstractSchoolingFish(EntityType<? extends AbstractSchoolingFish> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(5, new FollowFlockLeaderGoal(this));
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return this.getMaxSchoolSize();
    }

    public int getMaxSchoolSize() {
        return super.getMaxSpawnClusterSize();
    }

    @Override
    protected boolean canRandomSwim() {
        return !this.isFollower();
    }

    public boolean isFollower() {
        return this.leader != null && this.leader.isAlive();
    }

    public AbstractSchoolingFish startFollowing(AbstractSchoolingFish leader) {
        this.leader = leader;
        leader.addFollower();
        return leader;
    }

    public void stopFollowing() {
        if (this.leader == null) return; // Paper - Avoid NPE, plugins can now set the leader and certain fish goals might cause this method to be called
        this.leader.removeFollower();
        this.leader = null;
    }

    private void addFollower() {
        this.schoolSize++;
    }

    private void removeFollower() {
        this.schoolSize--;
    }

    public boolean canBeFollowed() {
        return this.hasFollowers() && this.schoolSize < this.getMaxSchoolSize();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.hasFollowers() && this.level().random.nextInt(200) == 1) {
            List<? extends AbstractFish> entitiesOfClass = this.level()
                .getEntitiesOfClass((Class<? extends AbstractFish>)this.getClass(), this.getBoundingBox().inflate(8.0, 8.0, 8.0));
            if (entitiesOfClass.size() <= 1) {
                this.schoolSize = 1;
            }
        }
    }

    public boolean hasFollowers() {
        return this.schoolSize > 1;
    }

    public boolean inRangeOfLeader() {
        return this.distanceToSqr(this.leader) <= 121.0;
    }

    public void pathToLeader() {
        if (this.isFollower()) {
            this.getNavigation().moveTo(this.leader, 1.0);
        }
    }

    public void addFollowers(Stream<? extends AbstractSchoolingFish> followers) {
        followers.limit(this.getMaxSchoolSize() - this.schoolSize).filter(fish -> fish != this).forEach(follower -> follower.startFollowing(this));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData spawnGroupData
    ) {
        super.finalizeSpawn(level, difficulty, spawnReason, spawnGroupData);
        if (spawnGroupData == null) {
            spawnGroupData = new AbstractSchoolingFish.SchoolSpawnGroupData(this);
        } else {
            this.startFollowing(((AbstractSchoolingFish.SchoolSpawnGroupData)spawnGroupData).leader);
        }

        return spawnGroupData;
    }

    public static class SchoolSpawnGroupData implements SpawnGroupData {
        public final AbstractSchoolingFish leader;

        public SchoolSpawnGroupData(AbstractSchoolingFish leader) {
            this.leader = leader;
        }
    }
}
