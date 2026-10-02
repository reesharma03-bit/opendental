package com.clinic.opendental.repository.ref;

import com.clinic.opendental.model.ref.Provider;
import com.clinic.opendental.model.ref.ProviderId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderRepository
        extends JpaRepository<Provider, ProviderId> {

    List<Provider> findByIdClinicId(UUID clinicId);
}