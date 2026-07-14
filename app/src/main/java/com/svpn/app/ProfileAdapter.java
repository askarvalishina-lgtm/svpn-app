package com.svpn.app;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.List;

public class ProfileAdapter extends ArrayAdapter<ConfigParser.VpnProfile> {
    private final Context context;
    private final List<ConfigParser.VpnProfile> profiles;

    public ProfileAdapter(Context context, List<ConfigParser.VpnProfile> profiles) {
        super(context, R.layout.item_profile, profiles);
        this.context = context;
        this.profiles = profiles;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_profile, parent, false);
        }

        ConfigParser.VpnProfile profile = profiles.get(position);

        TextView tvName = convertView.findViewById(R.id.tvProfileName);
        TextView tvDetails = convertView.findViewById(R.id.tvProfileDetails);
        ImageView ivIcon = convertView.findViewById(R.id.ivProtocolIcon);
        View selectorIndicator = convertView.findViewById(R.id.viewSelectorIndicator);

        tvName.setText(profile.name);
        tvDetails.setText(profile.type.toUpperCase() + "  |  " + profile.address + ":" + profile.port);

        // Customize indicators based on selected state
        if (profile.isSelected) {
            selectorIndicator.setVisibility(View.VISIBLE);
            convertView.setBackgroundResource(R.drawable.bg_item_selected);
        } else {
            selectorIndicator.setVisibility(View.INVISIBLE);
            convertView.setBackgroundResource(R.drawable.bg_item_normal);
        }

        // Set Protocol icons
        if ("vless".equalsIgnoreCase(profile.type)) {
            ivIcon.setImageResource(android.R.drawable.ic_lock_power_off);
        } else if ("ss".equalsIgnoreCase(profile.type)) {
            ivIcon.setImageResource(android.R.drawable.ic_lock_lock);
        } else if ("trojan".equalsIgnoreCase(profile.type)) {
            ivIcon.setImageResource(android.R.drawable.ic_dialog_alert);
        } else {
            ivIcon.setImageResource(android.R.drawable.ic_menu_preferences);
        }

        return convertView;
    }
}
