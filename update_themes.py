import os

files_to_update = [
    r'C:\Users\HP\Desktop\Suraksha Setu\Suraksha Setu\Shakti\app\src\main\res\layout\activity_volunteer_home.xml',
    r'C:\Users\HP\Desktop\Suraksha Setu\Suraksha Setu\Shakti\app\src\main\res\layout\activity_safe_route.xml',
    r'C:\Users\HP\Desktop\Suraksha Setu\Suraksha Setu\Shakti\app\src\main\res\layout\activity_emergency.xml'
]

logout_xml = '''
        <ImageView
            android:id="@+id/btnLogout"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:layout_marginEnd="16dp"
            android:layout_gravity="center_vertical"
            android:src="@drawable/ic_logout"
            app:tint="@color/text_secondary"
            android:clickable="true"
            android:background="?attr/selectableItemBackgroundBorderless" />
'''

for file_path in files_to_update:
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Apply bright theme color mappings
    content = content.replace('#0D0D1A', '@color/background')
    content = content.replace('#1A1A2E', '@color/surface')
    content = content.replace('#131320', '@color/surface')
    content = content.replace('#FFFFFF', '@color/text_primary')
    content = content.replace('#AAAAAA', '@color/text_secondary')
    content = content.replace('#2A2A3E', '@color/divider')
    content = content.replace('#2D1A1A', '@color/sos_red_light')

    # Add Logout icon to header in Volunteer Home only
    if 'activity_volunteer_home.xml' in file_path:
        search_str = '<androidx.cardview.widget.CardView\n            android:layout_width="48dp"'
        if search_str in content:
            content = content.replace(
                search_str,
                logout_xml + '\n        ' + search_str.replace('app:cardBackgroundColor="@color/divider"', 'app:cardBackgroundColor="@color/divider"')
            )
            # Fix card background while we are at it
            content = content.replace('app:cardBackgroundColor="#2A2A3E"', 'app:cardBackgroundColor="@color/divider"')

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
print("Updated XMLs successfully.")
