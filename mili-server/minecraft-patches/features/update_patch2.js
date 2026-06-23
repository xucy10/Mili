const fs = require('fs');
let content = fs.readFileSync('0007-Add-config-to-enable-tick-command.patch', 'utf8');

// Find the exact text around tickRegion
const idx = content.indexOf('this.tickRegion(tickCount, tickStart, scheduledEnd)');
if (idx < 0) {
    console.log('tickRegion not found');
    process.exit(1);
}

// Extract surrounding context
const start = content.lastIndexOf('@@ -498,6', idx);
const end = content.indexOf('@@ -', start + 1);
const section = content.substring(start, end > 0 ? end : start + 500);

// Replace within this section
const oldTickRegion = '                 this.tickRegion(tickCount, tickStart, scheduledEnd);\n                 // Luminol start - Add a config to enable tick command';
const newTickRegion = '                 // Mili start - Region Balancer\n+                if (fun.bm.mili.config.modules.experiment.RegionBalancerConfig.enabled) {\n+                    fun.bm.mili.utils.RegionBalancer.init();\n+                    fun.bm.mili.utils.RegionBalancer.submitAndWait(this, tickCount, () -> {\n+                        this.tickRegion(tickCount, tickStart, scheduledEnd);\n+                    });\n+                } else {\n+                    this.tickRegion(tickCount, tickStart, scheduledEnd);\n+                }\n+                // Mili end - Region Balancer\n                 // Luminol start - Add a config to enable tick command';

if (!content.includes(oldTickRegion)) {
    console.log('oldTickRegion not found');
    console.log(JSON.stringify(content.substring(idx - 50, idx + 200)));
    process.exit(1);
}

content = content.replace(oldTickRegion, newTickRegion);

// Also update the @@ line count
content = content.replace('@@ -498,6 +513,11 @@ public final class TickRegionScheduler {', 
                            '@@ -498,6 +513,20 @@ public final class TickRegionScheduler {');

fs.writeFileSync('0007-Add-config-to-enable-tick-command.patch', content, 'utf8');
console.log('OK');
