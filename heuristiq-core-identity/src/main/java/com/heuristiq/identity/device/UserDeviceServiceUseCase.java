package com.heuristiq.identity.device;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.device.repository.UserDeviceRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

public interface UserDeviceServiceUseCase {

    record ListByUserInput(String userId) {}
    record ListByUserOutput(List<UserDevice> devices) {}
    ListByUserOutput listByUser(ListByUserInput input);

    record GetInput(String id) {}
    record GetOutput(UserDevice device) {}
    GetOutput get(GetInput input);

    record DeleteInput(String id) {}
    record DeleteOutput() {}
    DeleteOutput delete(DeleteInput input);

    record TrustInput(String id, boolean trusted) {}
    record TrustOutput(UserDevice device) {}
    TrustOutput trust(TrustInput input);

    record BlockInput(String id, String reason) {}
    record BlockOutput(UserDevice device) {}
    BlockOutput block(BlockInput input);

    record UnblockInput(String id) {}
    record UnblockOutput(UserDevice device) {}
    UnblockOutput unblock(UnblockInput input);
}
