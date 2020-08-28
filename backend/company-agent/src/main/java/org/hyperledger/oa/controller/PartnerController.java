/**
 * Copyright (c) 2020 - for information on the respective copyright owner
 * see the NOTICE file and/or the repository at
 * https://github.com/hyperledger-labs/organizational-agent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hyperledger.oa.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;

import org.hyperledger.oa.api.PartnerAPI;
import org.hyperledger.oa.api.aries.AriesProof;
import org.hyperledger.oa.controller.api.partner.AddPartnerRequest;
import org.hyperledger.oa.controller.api.partner.PartnerCredentialType;
import org.hyperledger.oa.controller.api.partner.RequestCredentialRequest;
import org.hyperledger.oa.controller.api.partner.RequestProofRequest;
import org.hyperledger.oa.impl.PartnerManager;
import org.hyperledger.oa.impl.aries.AriesCredentialManager;
import org.hyperledger.oa.impl.aries.ProofManager;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;

@Controller("/api/partners")
@Tag(name = "Partner (Connection) Management")
@Validated
@Secured(SecurityRule.IS_AUTHENTICATED)
@ExecuteOn(TaskExecutors.IO)
public class PartnerController {

    @Inject
    private PartnerManager pm;

    @Inject
    private Optional<AriesCredentialManager> credM;

    @Inject
    private Optional<ProofManager> proofM;

    /**
     * Get known partners
     *
     * @return list of partners
     */
    @Get
    public HttpResponse<List<PartnerAPI>> getPartners() {
        return HttpResponse.ok(pm.getPartners());
    }

    /**
     * Get partner by id
     *
     * @param id the partner id
     * @return partner
     */
    @Get("/{id}")
    public HttpResponse<PartnerAPI> getPartnerbyId(@PathVariable String id) {
        Optional<PartnerAPI> partner = pm.getPartnerById(UUID.fromString(id));
        if (partner.isPresent()) {
            return HttpResponse.ok(partner.get());
        }
        return HttpResponse.notFound();
    }

    /**
     * Remove partner
     *
     * @param id the partner id
     * @return HTTP status, no body
     */
    @Delete("/{id}")
    public HttpResponse<Void> removePartner(@PathVariable String id) {
        pm.removePartnerById(UUID.fromString(id));
        return HttpResponse.ok();
    }

    /**
     * Add a new partner
     *
     * @param partner {@link AddPartnerRequest}
     * @return {@link PartnerAPI}
     */
    @Post
    public HttpResponse<PartnerAPI> addPartner(@Body AddPartnerRequest partner) {
        return HttpResponse.created(pm.addPartnerFlow(partner.getDid(), partner.getAlias()));
    }

    /**
     * Lookup/Preview a partners public profile before adding
     *
     * @param did the partners did
     * @return {@link PartnerAPI}
     */
    @Get("/lookup/{did}")
    public HttpResponse<PartnerAPI> lookupPartner(@PathVariable String did) {
        return HttpResponse.ok(pm.lookupPartner(did));
    }

    /**
     * Reload/Re- lookup a partners public profile
     *
     * @param id the partner id
     * @return {@link PartnerAPI}
     */
    @Get("/{id}/refresh")
    public HttpResponse<PartnerAPI> refreshPartner(@PathVariable String id) {
        final Optional<PartnerAPI> partner = pm.refreshPartner(UUID.fromString(id));
        if (partner.isPresent()) {
            return HttpResponse.ok(partner.get());
        }
        return HttpResponse.notFound();
    }

    /**
     * Aries: Request credential from partner
     *
     * @param id      the partner id
     * @param credReq {@link RequestCredentialRequest}
     * @return HTTP status
     */
    @Post("/{id}/credential-request")
    public HttpResponse<Void> requestCredential(
            @PathVariable String id,
            @Body RequestCredentialRequest credReq) {
        credM.ifPresent(mgmt -> mgmt.sendCredentialRequest(
                UUID.fromString(id),
                UUID.fromString(credReq.getDocumentId())));
        return HttpResponse.ok();
    }

    /**
     * Aries: Get credential types that the partner can issue
     *
     * @param id the partner id
     * @return HTTP status
     */
    @Get("/{id}/credential-types")
    public HttpResponse<List<PartnerCredentialType>> partnerCredentialTypes(@PathVariable String id) {
        if (credM.isPresent()) {
            final Optional<List<PartnerCredentialType>> credDefs = credM.get().getPartnerCredDefs(UUID.fromString(id));
            if (credDefs.isPresent()) {
                return HttpResponse.ok(credDefs.get());
            }
            return HttpResponse.notFound();
        }
        return HttpResponse.ok();
    }

    /**
     * Aries: Request proof from partner
     *
     * @param id  the partner id
     * @param req {@link RequestProofRequest}
     * @return HTTP status
     */
    @Post("/{id}/proof-request")
    public HttpResponse<Void> requestProof(
            @PathVariable String id,
            @Body RequestProofRequest req) {
        proofM.ifPresent(mgmt -> mgmt.sendPresentProofRequest(UUID.fromString(id), req.getCredentialDefinitionId()));
        return HttpResponse.ok();
    }

    /**
     * Aries: List proofs that the partner sent to me
     *
     * @param id the partner id
     * @return HTTP status
     */
    @Get("/{id}/proof")
    public HttpResponse<List<AriesProof>> getPartnerProofs(
            @PathVariable String id) {
        if (proofM.isPresent()) {
            return HttpResponse.ok(proofM.get().listPartnerProofs(UUID.fromString(id)));
        }
        return HttpResponse.notFound();
    }

    /**
     * Aries: Get a partners proof by id
     *
     * @param id      the partner id
     * @param proofId the proof id
     * @return HTTP status
     */
    @Get("/{id}/proof/{proofId}")
    public HttpResponse<AriesProof> getPartnerProofById(
            @PathVariable String id,
            @PathVariable String proofId) {
        if (proofM.isPresent()) {
            final Optional<AriesProof> proof = proofM.get().getPartnerProofById(UUID.fromString(proofId));
            if (proof.isPresent()) {
                return HttpResponse.ok(proof.get());
            }
        }
        return HttpResponse.notFound();
    }
}