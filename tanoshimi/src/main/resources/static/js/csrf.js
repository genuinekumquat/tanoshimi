/** CSRF 토큰을 붙여 요청을 보내는 공용 fetch 래퍼. */
(function () {
    const tokenMeta = document.querySelector('meta[name="_csrf"]');
    const headerMeta = document.querySelector('meta[name="_csrf_header"]');
    const token = tokenMeta ? tokenMeta.getAttribute('content') : '';
    const header = headerMeta ? headerMeta.getAttribute('content') : 'X-CSRF-TOKEN';

    async function request(url, options) {
        let response;
        try {
            response = await fetch(url, Object.assign({ credentials: 'same-origin', headers: {} }, options));
        } catch (networkError) {
            // fetch() 자체가 실패하면(네트워크 끊김, 서버 다운 등) 예외를 던지므로,
            // 여기서 잡아서 항상 같은 모양의 결과 객체를 반환한다 - 호출부에서 매번 try/catch 안 해도 되게.
            console.error('네트워크 요청 실패:', url, networkError);
            return { httpOk: false, success: false, message: '서버에 연결할 수 없습니다. 서버가 켜져 있는지 확인해 주세요.' };
        }
        let body = {};
        try { body = await response.json(); } catch (e) { body = { success: false, message: `서버 응답을 처리할 수 없습니다. (HTTP ${response.status})` }; }
        return { httpOk: response.ok, ...body };
    }

    window.api = {
        get(url) { return request(url, { method: 'GET' }); },
        post(url, payload) {
            const headers = { 'Content-Type': 'application/json' };
            if (token) headers[header] = token;
            return request(url, { method: 'POST', headers, body: JSON.stringify(payload) });
        },
        patch(url) {
            const headers = {};
            if (token) headers[header] = token;
            return request(url, { method: 'PATCH', headers });
        },
        // [v19] "내 여행" 수정(MyTripController)에 필요해 추가 - post와 같은 모양,
        // 메서드만 PUT. 기존 호출부(get/post/patch/del)는 그대로다.
        put(url, payload) {
            const headers = { 'Content-Type': 'application/json' };
            if (token) headers[header] = token;
            return request(url, { method: 'PUT', headers, body: JSON.stringify(payload) });
        },
        del(url) {
            const headers = {};
            if (token) headers[header] = token;
            return request(url, { method: 'DELETE', headers });
        }
    };
})();
