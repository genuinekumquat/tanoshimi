import re

# 1. PartyRepository
path = 'src/main/java/net/datasa/tanoshimi/repository/PartyRepository.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('List<PartyEntity> findByStatusOrderByDepartureDateAsc(PartyStatus status);', 'List<PartyEntity> findByStatusAndBlindedFalseOrderByDepartureDateAsc(PartyStatus status);')
text = text.replace('List<PartyEntity> findByRegionAndStatus(String region, PartyStatus status);', 'List<PartyEntity> findByRegionAndStatusAndBlindedFalse(String region, PartyStatus status);')
text = text.replace('where p.status = :status', 'where p.status = :status and p.blinded = false')

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

# 2. PartyController
path = 'src/main/java/net/datasa/tanoshimi/controller/PartyController.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('partyRepository.findByStatusOrderByDepartureDateAsc', 'partyRepository.findByStatusAndBlindedFalseOrderByDepartureDateAsc')
text = text.replace('partyRepository.findByRegionAndStatus', 'partyRepository.findByRegionAndStatusAndBlindedFalse')

# Guard for detail page
target_detail = 'PartyEntity party = partyRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));'
new_detail = '''PartyEntity party = partyRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        if (party.isBlinded()) throw new BusinessException(ErrorCode.PARTY_NOT_FOUND, "블라인드 처리된 파티입니다.");'''
text = text.replace(target_detail, new_detail)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

print("Party filtering applied!")