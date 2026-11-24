package com.heuristq.dinein.guest;

import com.heuristq.dinein.shared.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class GuestSessionArgumentResolver implements HandlerMethodArgumentResolver {

    private final GuestSessionService guestSessionService;

    public GuestSessionArgumentResolver(GuestSessionService guestSessionService) {
        this.guestSessionService = guestSessionService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentGuest.class)
                && GuestSession.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return guestSessionService.fromRequest(request)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "GUEST_SESSION_REQUIRED",
                        "Please scan the QR code on your table to continue"));
    }
}
