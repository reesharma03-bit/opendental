package com.clinic.opendental.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InsuranceBillingResourcesController.class)
@TestPropertySource(properties = "opendental.base-url=https://od.example/api/v1")
class InsuranceBillingResourcesControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private RestTemplate restTemplate;

    @Test
    void documentedReadsForwardToUpstreamPaths() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("ResponseKey", "retained")));
        List<String> urls = List.of(
                "/api/benefits?PlanNum=12", "/api/benefits/14",
                "/api/carriers", "/api/carriers/32",
                "/api/claimforms", "/api/claimforms/1",
                "/api/claimpayments", "/api/claimpayments/1822",
                "/api/claimprocs?ClaimNum=98567", "/api/claimprocs/1984257",
                "/api/claims?PatNum=23", "/api/claims/1",
                "/api/claimtrackings?ClaimNum=25",
                "/api/covcats", "/api/covcats/1",
                "/api/covspans?CovCatNum=10", "/api/covspans/40",
                "/api/deposits?DateDeposit=2025-12-29", "/api/deposits/83",
                "/api/discountplans", "/api/discountplans/2",
                "/api/discountplansubs?PatNum=56",
                "/api/eobattaches?ClaimPaymentNum=23",
                "/api/fees?FeeSched=13", "/api/fees/112",
                "/api/feescheds",
                "/api/insplans?PlanType=p", "/api/insplans/6",
                "/api/inssubs?Subscriber=485", "/api/inssubs/34",
                "/api/insverifies", "/api/insverifies/12",
                "/api/payments?PatNum=1337",
                "/api/payplancharges?PayPlanNum=21",
                "/api/payplanlinks?PayPlanNum=106", "/api/payplanlinks/243",
                "/api/payplans?PatNum=72", "/api/payplans/48",
                "/api/paysplits?PayNum=8567",
                "/api/statements?PatNum=1430", "/api/statements/1",
                "/api/substitutionlinks?PlanNum=33");

        for (String url : urls) {
            mockMvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ResponseKey", is("retained")));
        }
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/benefits/14")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class));
        verify(restTemplate).exchange(eq(URI.create(
                        "https://od.example/api/v1/claimprocs?ClaimNum=98567")),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class));
    }

    @Test
    void writesForwardUsingOpenDentalFieldNamesAndPreserveUpstreamStatusAndBody() throws Exception {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.status(201).body(Map.of("CarrierNum", 1, "CarrierName", "Dental Guard")));

        mockMvc.perform(post("/api/carriers").contentType("application/json")
                        .content("{\"CarrierName\":\"Dental Guard\",\"City\":\"Portland\",\"NoSendElect\":\"SendElect\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.CarrierNum", is(1)));
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/carriers")),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class));

        reset(restTemplate);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok().build());
        mockMvc.perform(delete("/api/benefits/75")).andExpect(status().isOk());
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/benefits/75")),
                eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Object.class));
    }

    @Test
    void documentedNamedSubRoutesForward() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("ResponseKey", "retained")));

        mockMvc.perform(post("/api/claimpayments/Batch").contentType("application/json")
                        .content("{\"claimNums\":[2547,2568],\"CheckAmt\":\"350.35\"}"))
                .andExpect(status().isOk());
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/claimpayments/Batch")),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class));

        mockMvc.perform(put("/api/claims/26/Status").contentType("application/json")
                        .content("{\"DateSent\":\"2021-09-13\"}"))
                .andExpect(status().isOk());
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/claims/26/Status")),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Object.class));

        mockMvc.perform(put("/api/payplans/343/Close")).andExpect(status().isOk());
        verify(restTemplate).exchange(eq(URI.create("https://od.example/api/v1/payplans/343/Close")),
                eq(HttpMethod.PUT), any(HttpEntity.class), eq(Object.class));

        mockMvc.perform(post("/api/eobattaches/DownloadSftp").contentType("application/json")
                        .content("{\"EobAttachNum\":10,\"SftpAddress\":\"MySftpSite/EOB.png\",\"SftpUsername\":\"u\",\"SftpPassword\":\"p\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void unsupportedMethodsAndInvalidInputsDoNotReachUpstream() throws Exception {
        mockMvc.perform(delete("/api/carriers/32")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/claimforms").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/feescheds/53")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/paysplits").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/claims/26/Bogus")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/claimpayments/Batch").contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/api/claims").param("Bogus", "1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/claims").param("ClaimStatus", "Z")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/insplans").param("PlanType", "z")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/payplans")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/substitutionlinks")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/eobattaches")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/insverifies").param("FKey", "10")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/claimpayments").param("SecDateTEdit", "2023-08-15"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/carriers").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/benefits").contentType("application/json")
                        .content("{\"PlanNum\":12,\"BenefitType\":\"CoInsurance\",\"CoverageLevel\":\"Individual\",\"Percent\":150}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/covspans").contentType("application/json")
                        .content("{\"CovCatNum\":37,\"FromCode\":\"D1499\",\"ToCode\":\"D1400\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/fees").contentType("application/json")
                        .content("{\"FeeSched\":263,\"CodeNum\":693}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/deposits").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/claimprocs/PendingSupplemental").contentType("application/json")
                        .content("{\"ClaimProcNum\":277}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/substitutionlinks").contentType("application/json")
                        .content("{\"PlanNum\":34,\"CodeNum\":6,\"SubstitutionCode\":\"D3002\",\"SubstOnlyIf\":\"Bogus\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/payments").contentType("application/json")
                        .content("{\"PatNum\":1337,\"PayAmt\":\"339\",\"ProcessStatus\":\"Nope\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void returnsUpstreamErrorsAndClearBadGatewayOnConnectivityFailure() throws Exception {
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Denied", null,
                        "{\"message\":\"upstream denied\"}".getBytes(), null));
        mockMvc.perform(get("/api/carriers"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("{\"message\":\"upstream denied\"}"));

        reset(restTemplate);
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(new ResourceAccessException("connection refused"));
        mockMvc.perform(get("/api/carriers"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error", is("Open Dental connectivity failure.")));
    }
}