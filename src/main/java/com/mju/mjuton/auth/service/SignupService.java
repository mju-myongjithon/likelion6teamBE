package com.mju.mjuton.auth.service;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.profile.service.ProfileService;
import com.mju.mjuton.profile.service.ProfileWriteLock;
import org.springframework.stereotype.Service;

@Service
public class SignupService {
	private final AuthService authService;
	private final ProfileService profileService;
	private final ProfileWriteLock profileWriteLock;

	public SignupService(AuthService authService, ProfileService profileService, ProfileWriteLock profileWriteLock) {
		this.authService = authService;
		this.profileService = profileService;
		this.profileWriteLock = profileWriteLock;
	}

	public User signup(String email, String verificationCode, String password,
			ProfileService.ProfileValues profileValues) {
		return profileWriteLock.execute(() -> {
			User user = authService.createVerifiedUser(email, verificationCode, password);
			profileService.createForSignup(user, profileValues);
			return user;
		});
	}
}
