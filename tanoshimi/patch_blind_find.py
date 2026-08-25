import re

# 1. Update PostRepository
path = 'src/main/java/net/datasa/tanoshimi/repository/PostRepository.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('findAllByOrderByCreatedAtDesc', 'findByBlindedFalseOrderByCreatedAtDesc')
text = text.replace('findByRegionOrderByCreatedAtDesc', 'findByBlindedFalseAndRegionOrderByCreatedAtDesc')

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

# 2. Update PostService
path = 'src/main/java/net/datasa/tanoshimi/service/PostService.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('findAllByOrderByCreatedAtDesc', 'findByBlindedFalseOrderByCreatedAtDesc')
text = text.replace('findByRegionOrderByCreatedAtDesc', 'findByBlindedFalseAndRegionOrderByCreatedAtDesc')
# Prevent finding blinded in detail? Maybe keep it for admin or the user themselves, but we should throw if blinded
text = text.replace('PostEntity post = postRepository.findWithUserById(postId).orElseThrow(', 
'''PostEntity post = postRepository.findWithUserById(postId).orElseThrow(
                () -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (post.isBlinded()) throw new BusinessException(ErrorCode.POST_NOT_FOUND, "블라인드 처리된 게시글입니다.");
        // trick to ignore next line: 
        if (false) throw ''')

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

# 3. Update PartyRepository
path = 'src/main/java/net/datasa/tanoshimi/repository/PartyRepository.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('findAll(Specification<PartyEntity> spec, Pageable pageable);', 
'''findAll(Specification<PartyEntity> spec, Pageable pageable);
    @EntityGraph(attributePaths = {"owner"})
    List<PartyEntity> findByBlindedFalseOrderByCreatedAtDesc();''')

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
    
print("Updated Repositories and Services for Blind")