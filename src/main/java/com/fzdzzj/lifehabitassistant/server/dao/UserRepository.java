package com.fzdzzj.lifehabitassistant.server.dao;
import com.fzdzzj.lifehabitassistant.pojo.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, Long> { Optional<User> findByUsername(String username); boolean existsByUsername(String username); }
