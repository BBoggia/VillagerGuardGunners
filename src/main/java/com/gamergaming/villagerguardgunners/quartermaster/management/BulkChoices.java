package com.gamergaming.villagerguardgunners.quartermaster.management;

import com.gamergaming.villagerguardgunners.quartermaster.service.*;
import net.minecraft.resources.ResourceLocation;

/** Each Keep choice preserves that field independently, including mixed selections. */
public record BulkChoices(int policy, ResourceLocation profile, int replacement, int assignment) {
    public static final BulkChoices KEEP=new BulkChoices(-1,null,-1,-1);
    public BulkChoices {
        if(policy< -1||policy>2||replacement< -1||replacement>1||assignment< -1||assignment>1
                ||profile!=null&&profile.toString().length()>96)throw new IllegalArgumentException("Invalid bulk choices");
    }
    public ServiceSettings resolve(ServiceSettings current,ServiceStationAssignment station) {
        return new ServiceSettings(policy<0?current.policy():GuardServiceState.Policy.values()[policy],
                profile==null?current.profile():profile,replacement<0?current.replaceExisting():replacement==1,
                assignment<0?current.assignment():assignment==0?null:station);
    }
}
