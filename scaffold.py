import os

package_name = 'com.example.shaketosave'
src_dir = r'C:\Users\HP\Desktop\Suraksha Setu\Suraksha Setu\Shakti\app\src\main\java\com\example\shaketosave'
res_dir = r'C:\Users\HP\Desktop\Suraksha Setu\Suraksha Setu\Shakti\app\src\main\res\layout'
manifest_path = r'C:\Users\HP\Desktop\Suraksha Setu\Suraksha Setu\Shakti\app\src\main\AndroidManifest.xml'

activities = [
    ('CommunityHelpActivity', 'activity_community_help'),
    ('SafePlacesActivity', 'activity_safe_places'),
    ('SafetyDashboardActivity', 'activity_safety_dashboard'),
    ('EmergencyContactsActivity', 'activity_emergency_contacts'),
    ('PrivacySecurityActivity', 'activity_privacy_security'),
    ('ReportIncidentActivity', 'activity_report_incident'),
    ('NotificationsActivity', 'activity_notifications'),
    ('CommunityRecognitionActivity', 'activity_community_recognition'),
    ('CitizenProfileActivity', 'activity_citizen_profile')
]

java_template = '''package {package_name};

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class {activity_name} extends AppCompatActivity {{
    @Override
    protected void onCreate(Bundle savedInstanceState) {{
        super.onCreate(savedInstanceState);
        setContentView(R.layout.{layout_name});
    }}
}}
'''

xml_template = '''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background"
    android:padding="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="{title}"
        android:textSize="24sp"
        android:textStyle="bold"
        android:textColor="@color/text_primary" />
        
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Mock Screen - Pending API Integration"
        android:textSize="14sp"
        android:textColor="@color/text_secondary"
        android:layout_marginTop="8dp" />

</LinearLayout>
'''

# Read manifest
with open(manifest_path, 'r', encoding='utf-8') as f:
    manifest_content = f.read()

for activity_name, layout_name in activities:
    # Write Java
    with open(os.path.join(src_dir, f'{activity_name}.java'), 'w', encoding='utf-8') as f:
        f.write(java_template.format(package_name=package_name, activity_name=activity_name, layout_name=layout_name))
        
    # Write XML
    title = activity_name.replace('Activity', '')
    with open(os.path.join(res_dir, f'{layout_name}.xml'), 'w', encoding='utf-8') as f:
        f.write(xml_template.format(title=title))
        
    # Add to manifest if not exists
    if activity_name not in manifest_content:
        activity_tag = f'''        <activity
            android:name=".{activity_name}"
            android:exported="false"
            android:theme="@style/Theme.ShakeToSave" />
'''
        manifest_content = manifest_content.replace('</application>', activity_tag + '\n    </application>')

# Write updated manifest
with open(manifest_path, 'w', encoding='utf-8') as f:
    f.write(manifest_content)

print('Successfully scaffolded activities.')
