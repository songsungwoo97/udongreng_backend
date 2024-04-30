package rank.example.rank.domain.match.service;

import com.querydsl.core.types.dsl.BooleanExpression;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rank.example.rank.domain.match.dto.*;
import rank.example.rank.domain.match.entity.*;
import rank.example.rank.domain.match.exception.MatchNotFoundException;
import rank.example.rank.domain.match.repository.MatchApplicationRepository;
import rank.example.rank.domain.match.repository.MatchRepository;
import rank.example.rank.domain.match.repository.MatchSetRepository;
import rank.example.rank.domain.user.entity.User;
import rank.example.rank.domain.user.exception.UserNotFoundException;
import rank.example.rank.domain.user.repository.UserRepository;
import rank.example.rank.domain.userDetail.entity.Tier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MatchService {
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final MatchApplicationRepository matchApplicationRepository;
    private final MatchSetRepository matchSetRepository;

    public MatchDto createMatch(MatchCreateRequestDto requestDto) {
        User initiator = userRepository.findById(requestDto.getInitiatorId())
                .orElseThrow(() -> new UserNotFoundException("Initiator not found id: " + requestDto.getInitiatorId()));
        Match match = Match.builder()
                .title(requestDto.getMatchName())
                .initiator(initiator)
                .matchDateTime(LocalDateTime.now())
                .gender(requestDto.getGender())
                .tier(requestDto.getTier())
                .location(requestDto.getLocation())
                .description(requestDto.getDescription())
//                .type(requestDto.getType())
                .status(MatchStatus.IN_PROGRESS)
                .build();

        match = matchRepository.save(match);
        return MatchDto.fromEntity(match);
    }

    @Transactional
    public void applyForMatch(Long matchId, Long applicantId) {
        User applicant = userRepository.findById(applicantId)
                .orElseThrow(() -> new UserNotFoundException("유저 못찾음 id: " + applicantId));

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException("매치 못찾음 id:" + matchId));

        MatchApplication application = new MatchApplication(applicant, match, MatchApplicationStatus.PENDING);
        matchApplicationRepository.save(application);
    }

    public List<MatchApplicationDto> getApplicationsForMatch(Long matchId) {
        return matchApplicationRepository.findMatchApplicationsByMatchId(matchId);
    }

    public MatchAcceptResponseDto acceptMatch(MatchAcceptRequestDto requestDto) {
        Match match = matchRepository.findById(requestDto.getMatchId())
                .orElseThrow(() -> new MatchNotFoundException("매치를 찾을 수 없음 id: " + requestDto.getMatchId()));
        User opponent = userRepository.findById(requestDto.getOpponentId())
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없음 id: " + requestDto.getOpponentId()));
        MatchApplication matchApplication = matchApplicationRepository.findById(requestDto.getMatchApplicationId())
                .orElseThrow(() -> new EntityNotFoundException("매치신청 없음"));

        match.setOpponent(opponent);
        matchApplication.setStatus(MatchApplicationStatus.APPROVED);
        match.setStatus(MatchStatus.IN_GAME);
        matchApplicationRepository.save(matchApplication);
        matchRepository.save(match);

        return MatchAcceptResponseDto.of(match, "매치가 성공적으로 수락됨.");
    }

    public MatchDto getMatchDetail(Long matchId) {
        Match match = matchRepository.findById(matchId).orElseThrow(() -> new MatchNotFoundException("매치 못찾음 id: " + matchId));
        return MatchDto.fromEntity(match);
    }

    public Page<MatchDto> findMatchesByCriteria(MatchCondition matchCondition, Pageable pageable) {
        QMatch match = QMatch.match;
        BooleanExpression predicate = tierEq(matchCondition.getTier())
                .and(genderEq(matchCondition.getGender()))
                .and(locationEq(matchCondition.getLocation()));
        return matchRepository.findAll(predicate, pageable)
                .map(MatchDto::fromEntity);
    }

    private BooleanExpression tierEq(Tier tier) {
        return tier != null ? QMatch.match.tier.eq(tier) : null;
    }

    private BooleanExpression genderEq(String gender) {
        return gender != null ? QMatch.match.gender.eq(gender) : null;
    }

    private BooleanExpression locationEq(String location) {
        return location != null ? QMatch.match.location.eq(location) : null;
    }

    @Transactional
    public List<MatchSetDto> addSetToMatch(Long matchId, int setNumber, int initiatorScore, int opponentScore) {
        Match match = matchRepository.findById(matchId).orElseThrow();
        MatchSet set = new MatchSet(setNumber, initiatorScore, opponentScore, match);
        match.getMatchSets().add(set);
        matchSetRepository.save(set);
        matchRepository.save(match);
        return match.getMatchSets()
                .stream().map(MatchSetDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public MatchDto finalizeMatch(Long matchId) {
        Match match = matchRepository.findById(matchId).orElseThrow();
        calculateMatchOutcome(match);
        match.setStatus(MatchStatus.COMPLETED);
        log.info("winnerId = {}", match.getWinner().getId());
        matchRepository.save(match);
        return MatchDto.fromEntityFinish(match);
    }

    private void calculateMatchOutcome(Match match) {
        int initiatorWins = 0;
        int opponentWins = 0;

        for (MatchSet set : match.getMatchSets()) {
            if (set.getInitiatorScore() > set.getOpponentScore()) {
                initiatorWins++;
            } else if (set.getInitiatorScore() < set.getOpponentScore()) {
                opponentWins++;
            }
        }

        if (initiatorWins > opponentWins) {
            match.setWinner(match.getInitiator());
            match.setLoser(match.getOpponent());
        } else if (opponentWins > initiatorWins) {
            match.setWinner(match.getOpponent());
            match.setLoser(match.getInitiator());
        }
        matchRepository.save(match);
    }
}