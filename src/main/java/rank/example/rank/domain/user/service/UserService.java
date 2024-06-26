package rank.example.rank.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rank.example.rank.domain.user.dto.AddUserInfoRequestDto;
import rank.example.rank.domain.user.dto.UpdateUserInfoDto;
import rank.example.rank.domain.user.dto.UserDto;
import rank.example.rank.domain.user.entity.User;
import rank.example.rank.domain.user.entity.UserType;
import rank.example.rank.domain.user.exception.UserNotFoundException;
import rank.example.rank.domain.user.repository.UserRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;

    public User addUserInfo(AddUserInfoRequestDto addUserInfoRequestDto) {
        User target = userRepository.findById(addUserInfoRequestDto.getId()).orElseThrow(RuntimeException::new);
        target.setAddress(addUserInfoRequestDto.getAddress());
        target.setAge(addUserInfoRequestDto.getAge());
        target.setGender(addUserInfoRequestDto.getGender());
        target.setPhoneNumber(addUserInfoRequestDto.getPhone());
        target.setUserType(UserType.ROLE_USER);
        return userRepository.save(target);
    }

    public User updateUserInfo(UpdateUserInfoDto dto) {
        User target = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없음"));
        target.setAddress(dto.getAddress());
        target.setAge(dto.getAge());
        target.setGender(dto.getGender());
        target.setPhoneNumber(dto.getPhone());
        return userRepository.save(target);
    }
}
