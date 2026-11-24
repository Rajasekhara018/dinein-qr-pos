package com.heuristiq.uam.device;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.device.repository.UserDeviceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserDeviceService implements UserDeviceServiceUseCase {
    private final UserDeviceRepository deviceRepository;

    public UserDeviceService(UserDeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public ListByUserOutput listByUser(ListByUserInput input) {
        String userId = input.userId();
        return new ListByUserOutput(deviceRepository.findByUserId(userId));
    }

    public GetOutput get(GetInput input) {
        String id = input.id();
        return new GetOutput(deviceRepository.findById(id).orElseThrow(() -> UamException.notFound("Device not found")));
    }

    public DeleteOutput delete(DeleteInput input) {
        String id = input.id();
        deviceRepository.delete(get(new GetInput(id)).device());
        return new DeleteOutput();
    }

    public TrustOutput trust(TrustInput input) {
        String id = input.id();
        boolean trusted = input.trusted();
        UserDevice device = get(new GetInput(id)).device();
        device.setTrusted(trusted);
        device.setUpdatedAt(LocalDateTime.now());
        return new TrustOutput(deviceRepository.save(device));
    }

    public BlockOutput block(BlockInput input) {
        String id = input.id();
        String reason = input.reason();
        UserDevice device = get(new GetInput(id)).device();
        device.setBlocked(true);
        device.setBlockReason(reason);
        device.setUpdatedAt(LocalDateTime.now());
        return new BlockOutput(deviceRepository.save(device));
    }

    public UnblockOutput unblock(UnblockInput input) {
        String id = input.id();
        UserDevice device = get(new GetInput(id)).device();
        device.setBlocked(false);
        device.setBlockReason(null);
        device.setUpdatedAt(LocalDateTime.now());
        return new UnblockOutput(deviceRepository.save(device));
    }
}
