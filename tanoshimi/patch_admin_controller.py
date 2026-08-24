import re

try:
    with open('src/main/java/net/datasa/tanoshimi/controller/AdminController.java', 'r', encoding='utf-8') as f:
        content = f.read()

    # We need to add the GetMapping for banners
    get_banners = """
    // ============================================
    // BANNERS
    // ============================================
    @GetMapping("/banners")
    public String banners(@AuthenticationPrincipal CustomUserDetails admin, Model model) {
        model.addAttribute("adminName", admin.getDisplayName());
        model.addAttribute("banners", bannerRepository.findAllByOrderBySortOrderAsc());
        return "admin/banners";
    }

    @PostMapping("/banners")
"""
    
    # Replace old Banner comment block with new one containing GET
    old_banner_block_regex = r"// ============================================\s*// BANNERS\s*// ============================================\s*@PostMapping\(\"/banners\"\)"
    content = re.sub(old_banner_block_regex, get_banners.strip(), content)

    # I should also remove banners from the dashboard GetMapping if I want, but it's harmless to keep it in model.
    # We can just leave model.addAttribute("banners", ...) in GET /admin for now, it's ignored by users.html.

    with open('src/main/java/net/datasa/tanoshimi/controller/AdminController.java', 'w', encoding='utf-8') as f:
        f.write(content)
        
    print("AdminController.java patched")
except Exception as e:
    import traceback
    traceback.print_exc()
