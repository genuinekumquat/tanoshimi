package net.datasa.tanoshimi.util;

// 나중에 실제 API로 교체할 때 이 인터페이스를 구현하는 RealGeminiClient만 만들면 됩니다.
public interface GeminiClient {
	String ask(String prompt);
}

