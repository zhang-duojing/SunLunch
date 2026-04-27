package com.sunlunch.sunlunch.repository;
import com.sunlunch.sunlunch.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface UserRepository extends JpaRepository<User,Long> {
    User findByEmail(String email);
    User findByEmailAndPassword(String email,String password);
    User findByEmailAndPasswordAndDeletedFalse(String email, String password);
    User findByEmailAndPasswordAndDeletedFalseAndEnabledTrue(String email, String password);
    List<User> findByDeletedFalseOrderByIdAsc();
    Optional<User> findByIdAndDeletedFalse(Long id);
    
    User findByEmailAndResetToken(String email,String resetToken);
}
