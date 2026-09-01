path_html = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/resources/templates/planner/index.html'
with open(path_html, 'r', encoding='utf-8') as f:
    text_html = f.read()

import re
text_html = re.sub(r'<button type="button" id="btn-pay" class="btn btn-forest btn-sm" th:style=".+?">투어결제하기</button>\s*', '', text_html)

with open(path_html, 'w', encoding='utf-8') as f:
    f.write(text_html)

path_service = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/service/TripPlannerService.java'
with open(path_service, 'r', encoding='utf-8') as f:
    text_service = f.read()

pattern = """    @Transactional
    public void submitForPayment(TripScheduleEntity schedule) {
        if (!schedule.isDraft()) {
            throw new net.datasa.tanoshimi.exception.BusinessException(net.datasa.tanoshimi.exception.ErrorCode.INVALID_INPUT, "이미 제출 완료된 시간표입니다.");
        }

        List<TripScheduleItemEntity> items = itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule);
        int totalKrw = items.stream().filter(i -> i.getSource() == ScheduleItemSource.activity)
                .mapToInt(TripScheduleItemEntity::getPriceKrw).sum();
        int totalJpy = items.stream().filter(i -> i.getSource() == ScheduleItemSource.activity)
                .mapToInt(TripScheduleItemEntity::getPriceJpy).sum();

        PartyEntity party = resolveParty(schedule);
        List<UserEntity> members = resolveMembers(schedule, party);

        long krwPayerCount = members.stream().filter(m -> m.getNationality() != Nationality.JP).count();
        long jpyPayerCount = members.size() - krwPayerCount;

        int krwShare = krwPayerCount == 0 ? 0 : totalKrw / (int) krwPayerCount;
        int krwRemainder = krwPayerCount == 0 ? 0 : totalKrw % (int) krwPayerCount;
        int jpyShare = jpyPayerCount == 0 ? 0 : totalJpy / (int) jpyPayerCount;
        int jpyRemainder = jpyPayerCount == 0 ? 0 : totalJpy % (int) jpyPayerCount;

        boolean krwRemainderGiven = false;
        boolean jpyRemainderGiven = false;

        for (UserEntity member : members) {
            boolean isJpy = member.getNationality() == Nationality.JP;
            int share = isJpy ? jpyShare : krwShare;
            int amount = share;

            if (!isJpy && krwRemainder > 0 && !krwRemainderGiven) {
                amount += krwRemainder;
                krwRemainderGiven = true;
            } else if (isJpy && jpyRemainder > 0 && !jpyRemainderGiven) {
                amount += jpyRemainder;
                jpyRemainderGiven = true;
            }

            if (amount > 0) {
                paymentRepository.save(new TripSchedulePaymentEntity(
                        schedule, member, isJpy ? Currency.JPY : Currency.KRW, amount));
            }
        }
        schedule.submit();
        scheduleRepository.save(schedule);
    }"""

replacement = """    @Transactional
    public void submitForPayment(TripScheduleEntity schedule) {
        if (!schedule.isDraft()) {
            throw new net.datasa.tanoshimi.exception.BusinessException(net.datasa.tanoshimi.exception.ErrorCode.INVALID_INPUT, "이미 제출 완료된 시간표입니다.");
        }
        schedule.submit();  // Optional: logical transition
        schedule.confirm(); // Directly confirm without payment
        scheduleRepository.save(schedule);
    }"""

if pattern in text_service:
    text_service = text_service.replace(pattern, replacement)
else:
    # Use re to lazily match it just in case of formatting
    text_service = re.sub(r'public void submitForPayment\(TripScheduleEntity schedule\) \{.+?scheduleRepository\.save\(schedule\);\s*\}', replacement.replace('    @Transactional\n', ''), text_service, flags=re.DOTALL)

with open(path_service, 'w', encoding='utf-8') as f:
    f.write(text_service)

path_ctrl = 'C:/Users/kean4/Documents/GitHub/tanoshimi/tanoshimi/src/main/java/net/datasa/tanoshimi/controller/PlannerController.java'
with open(path_ctrl, 'r', encoding='utf-8') as f:
    text_ctrl = f.read()

text_ctrl = text_ctrl.replace('ApiResponse.okMessage("제출되었습니다. 결제를 진행해주세요.");', 'ApiResponse.okMessage("계획표가 최종 확정되었습니다.");')

with open(path_ctrl, 'w', encoding='utf-8') as f:
    f.write(text_ctrl)
