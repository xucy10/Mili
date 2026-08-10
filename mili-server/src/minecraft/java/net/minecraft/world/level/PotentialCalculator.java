package net.minecraft.world.level;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;

public class PotentialCalculator {
    private final List<PotentialCalculator.PointCharge> charges = Lists.newArrayList();

    public void addCharge(BlockPos pos, double charge) {
        if (charge != 0.0) {
            this.charges.add(new PotentialCalculator.PointCharge(pos, charge));
        }
    }

    public double getPotentialEnergyChange(BlockPos pos, double charge) {
        if (charge == 0.0) {
            return 0.0;
        } else {
            double d = 0.0;

            for (PotentialCalculator.PointCharge pointCharge : this.charges) {
                d += pointCharge.getPotentialChange(pos);
            }

            return d * charge;
        }
    }

    static class PointCharge {
        private final BlockPos pos;
        private final double charge;

        public PointCharge(BlockPos pos, double charge) {
            this.pos = pos;
            this.charge = charge;
        }

        public double getPotentialChange(BlockPos pos) {
            double d = this.pos.distSqr(pos);
            return d == 0.0 ? Double.POSITIVE_INFINITY : this.charge / Math.sqrt(d);
        }
    }
}
