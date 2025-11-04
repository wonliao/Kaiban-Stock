package com.kanban.security;

import com.kanban.domain.entity.User;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        // 先嘗試用 username 查詢，如果找不到再用 email 查詢
        User user = userRepository.findByUsername(usernameOrEmail)
                .orElse(userRepository.findByEmail(usernameOrEmail)
                        .orElseThrow(() -> new UsernameNotFoundException("使用者不存在: " + usernameOrEmail)));
        
        return UserPrincipal.create(user);
    }
    
    @Transactional
    public UserDetails loadUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("使用者不存在: " + id));
        
        return UserPrincipal.create(user);
    }
}