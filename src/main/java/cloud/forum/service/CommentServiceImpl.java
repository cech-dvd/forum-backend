package cloud.forum.service;

import cloud.forum.dataTransferObjects.CommentAttitudeDto;
import cloud.forum.domain.Comment;
import cloud.forum.domain.CommentAttitude;
import cloud.forum.domain.LemonUser;
import cloud.forum.domain.Post;
import cloud.forum.repository.CommentAttitudeRepository;
import cloud.forum.repository.CommentRepository;
import com.naturalprogrammer.spring.lemon.LemonService;
import com.naturalprogrammer.spring.lemon.commons.security.UserDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static cloud.forum.domain.enums.Attitude.*;

@Service("commentService")
@Transactional
public class CommentServiceImpl implements CommentService {
    private final CommentRepository repository;
    private final LemonService<LemonUser, Long> lemonService;
    private final CommentAttitudeRepository attitudeRepository;

    public CommentServiceImpl(CommentRepository repository, LemonService<LemonUser, Long> lemonService, CommentAttitudeRepository attitudeRepository) {
        this.repository = repository;
        this.lemonService = lemonService;
        this.attitudeRepository = attitudeRepository;
    }

    @Override
    public Page<CommentAttitudeDto> getCommentByPost(Post post, UserDto user, Pageable pageable) {

        Page<Comment> comments = repository.findAllByPost(post, pageable);

        return Optional.ofNullable(user).map(a -> {
            LemonUser lemonUser = lemonService.findUserById(user.getId()).orElseThrow(IllegalStateException::new);
            return comments.map(comment -> attitudeRepository.findByOwnerAndComment(lemonUser, comment)
                    .map(commentAttitude -> new CommentAttitudeDto(lemonUser.getId(), lemonUser.getName(), commentAttitude.getAttitude(), comment))
                    .orElseGet(() -> new CommentAttitudeDto(lemonUser.getId(), lemonUser.getName(), NEUTRAL, comment)));
        }).orElseGet(() -> comments.map(c -> new CommentAttitudeDto(null, null, NEUTRAL, c)));
    }

    @Override
    public CommentAttitudeDto like(Comment comment, LemonUser user) {

        CommentAttitude commentAttitude = attitudeRepository.findByOwnerAndComment(user, comment)
                .map(a -> {
                    switch (a.getAttitude()) {
                        case LIKE:
                            break;
                        case DISLIKE:
                            comment.setDislikes(comment.getDislikes() - 1);
                            comment.setLikes(comment.getLikes() + 1);
                            break;
                        case NEUTRAL:
                            comment.setLikes(comment.getLikes() + 1);
                            break;
                    }
                    a.setAttitude(LIKE);
                    return a;
                })
                .orElseGet(() -> {
                    comment.setLikes(comment.getLikes() + 1);
                    return new CommentAttitude(user, comment, LIKE);
                });
        attitudeRepository.saveAndFlush(commentAttitude);
        Comment result = repository.saveAndFlush(comment);
        return new CommentAttitudeDto(user.getId(), user.getName(), LIKE, result);
    }

    @Override
    public CommentAttitudeDto dislike(Comment comment, LemonUser user) {
        CommentAttitude commentAttitude = attitudeRepository.findByOwnerAndComment(user, comment)
                .map(a -> {
                    switch (a.getAttitude()) {
                        case DISLIKE:
                            break;
                        case LIKE:
                            comment.setLikes(comment.getLikes() - 1);
                            comment.setDislikes(comment.getDislikes() + 1);
                            break;
                        case NEUTRAL:
                            comment.setDislikes(comment.getDislikes() + 1);
                            break;
                    }
                    a.setAttitude(DISLIKE);
                    return a;
                }).orElseGet(() -> {
                    comment.setDislikes(comment.getDislikes() + 1);
                    return new CommentAttitude(user, comment, DISLIKE);
                });
        attitudeRepository.saveAndFlush(commentAttitude);
        Comment result = repository.saveAndFlush(comment);
        return new CommentAttitudeDto(user.getId(), user.getName(), DISLIKE, result);
    }

    @Override
    public CommentAttitudeDto createComment(Comment comment, UserDto user) {
        LemonUser lemonUser = lemonService.findUserById(user.getId()).orElseThrow(IllegalStateException::new);
        Comment com = lemonService.findUserById(user.getId())
                .map(u -> {
                    comment.setUserId(u.getId());
                    comment.setOwner(u.getName());
                    return comment;
                })
                .map(repository::saveAndFlush)
                .orElseThrow(() -> new IllegalArgumentException("User is required"));
        return new CommentAttitudeDto(lemonUser.getId(),lemonUser.getName(),NEUTRAL,com);
    }

    @Override
    public void deleteComment(Comment comment, LemonUser lemonUser) {
        if(lemonUser.getId().equals(comment.getUserId()))
            repository.delete(comment);
        else throw new IllegalArgumentException("User is not the owner");
    }


}
