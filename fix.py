import os

files = [
    r'C:\Users\HP\Desktop\Suraksha Setu\Suraksha Setu\Shakti\app\src\main\res\layout\activity_citizen_home.xml',
    r'C:\Users\HP\Desktop\Suraksha Setu\Suraksha Setu\Shakti\app\src\main\res\layout\activity_safe_route.xml',
    r'C:\Users\HP\Desktop\Suraksha Setu\Suraksha Setu\Shakti\app\src\main\res\layout\activity_safe_walk.xml'
]

for filepath in files:
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    content = content.replace('@drawable/ic_map"', '@drawable/ic_map_nav"')
    content = content.replace('@drawable/ic_walk"', '@drawable/ic_walk_nav"')
    content = content.replace('@color/bottom_nav_color', '@color/primary')
    content = content.replace('@drawable/ic_location"', '@drawable/ic_map_nav"')
    content = content.replace('@drawable/ic_shield_check"', '@drawable/ic_shield"')

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

print('Updated layout xmls')
