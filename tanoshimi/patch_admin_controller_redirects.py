import re

try:
    with open('src/main/java/net/datasa/tanoshimi/controller/AdminController.java', 'r', encoding='utf-8') as f:
        content = f.read()

    # Change exact strings for redirects
    content = content.replace('return "redirect:/admin?error=bannerLimit";', 'return "redirect:/admin/banners?error=bannerLimit";')
    content = content.replace('bannerRepository.save(banner);\n        }\n        return "redirect:/admin";\n    }', 'bannerRepository.save(banner);\n        }\n        return "redirect:/admin/banners";\n    }')
    content = content.replace('bannerRepository.deleteById(id);\n        return "redirect:/admin";\n    }', 'bannerRepository.deleteById(id);\n        return "redirect:/admin/banners";\n    }')

    with open('src/main/java/net/datasa/tanoshimi/controller/AdminController.java', 'w', encoding='utf-8') as f:
        f.write(content)
        
    print("AdminController redirect patched")
except Exception as e:
    import traceback
    traceback.print_exc()
