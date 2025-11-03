package com.kanban.client;

import com.kanban.dto.twse.TwseApiResponse;
import com.kanban.dto.twse.TwseStockData;
import com.kanban.exception.StockNotFoundException;
import com.kanban.exception.TwseMcpException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class TwseMcpClientTest {
    
    private MockWebServer mockWebServer;
    private TwseMcpClient twseMcpClient;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        
        twseMcpClient = new TwseMcpClient(webClient);
        objectMapper = new ObjectMapper();
    }
    
    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
    
    @Test
    void getStockData_Success() throws JsonProcessingException, ExecutionException, InterruptedException {
        // Arrange
        TwseApiResponse<List<String>> mockResponse = createMockResponse();
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        // Act
        CompletableFuture<TwseStockData> future = twseMcpClient.getStockData("2330");
        TwseStockData result = future.get();
        
        // Assert
        assertNotNull(result);
        assertEquals("2330", result.getCode());
        assertEquals("台積電", result.getName());
        assertEquals(new BigDecimal("580.00"), result.getClosingPrice());
        assertNotNull(result.getTradeVolume());
    }
    
    @Test
    void getStockData_StockNotFound() throws JsonProcessingException {
        // Arrange
        TwseApiResponse<List<String>> mockResponse = createMockResponse();
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        // Act & Assert
        CompletableFuture<TwseStockData> future = twseMcpClient.getStockData("9999");
        
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof StockNotFoundException);
        assertEquals("9999", ((StockNotFoundException) exception.getCause()).getStockCode());
    }
    
    @Test
    void getStockData_ServerError() {
        // Arrange
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        
        // Act & Assert
        CompletableFuture<TwseStockData> future = twseMcpClient.getStockData("2330");
        
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof TwseMcpException);
    }
    
    @Test
    void getBatchStockData_Success() throws JsonProcessingException, ExecutionException, InterruptedException {
        // Arrange
        TwseApiResponse<List<String>> mockResponse = createMockResponse();
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        // Act
        CompletableFuture<List<TwseStockData>> future = twseMcpClient.getBatchStockData(List.of("2330", "2317"));
        List<TwseStockData> result = future.get();
        
        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("2330", result.get(0).getCode());
        assertEquals("2317", result.get(1).getCode());
    }
    
    @Test
    void validateStockCode_Valid() throws JsonProcessingException, ExecutionException, InterruptedException {
        // Arrange
        TwseApiResponse<List<String>> mockResponse = createMockResponse();
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        // Act
        CompletableFuture<Boolean> future = twseMcpClient.validateStockCode("2330");
        Boolean result = future.get();
        
        // Assert
        assertTrue(result);
    }
    
    @Test
    void validateStockCode_Invalid() throws JsonProcessingException, ExecutionException, InterruptedException {
        // Arrange
        TwseApiResponse<List<String>> mockResponse = createMockResponse();
        mockWebServer.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(mockResponse))
                .addHeader("Content-Type", "application/json"));
        
        // Act
        CompletableFuture<Boolean> future = twseMcpClient.validateStockCode("9999");
        Boolean result = future.get();
        
        // Assert
        assertFalse(result);
    }
    
    private TwseApiResponse<List<String>> createMockResponse() {
        TwseApiResponse<List<String>> response = new TwseApiResponse<>();
        response.setStatus("OK");
        response.setDate("20241115");
        response.setTitle("個股日成交資訊");
        response.setFields(List.of("證券代號", "證券名稱", "成交股數", "成交筆數", "成交金額", 
                                 "開盤價", "最高價", "最低價", "收盤價", "漲跌(+/-)", "漲跌價差"));
        
        // 模擬股票資料
        List<List<String>> data = List.of(
            List.of("2330", "台積電", "25000000", "15000", "14500000000", 
                   "575.00", "585.00", "570.00", "580.00", "+", "5.00"),
            List.of("2317", "鴻海", "30000000", "12000", "3300000000", 
                   "108.00", "112.00", "107.00", "110.00", "+", "2.00")
        );
        response.setData(data);
        
        return response;
    }
}