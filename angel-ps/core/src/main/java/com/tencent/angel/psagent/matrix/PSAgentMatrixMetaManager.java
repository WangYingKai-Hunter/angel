/*
 * Tencent is pleased to support the open source community by making Angel available.
 *
 * Copyright (C) 2017-2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in 
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/Apache-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */


package com.tencent.angel.psagent.matrix;

import com.google.protobuf.ServiceException;
import com.tencent.angel.PartitionKey;
import com.tencent.angel.ml.matrix.MatrixMeta;
import com.tencent.angel.ml.matrix.MatrixMetaManager;
import com.tencent.angel.ml.matrix.MatrixMetaUtils;
import com.tencent.angel.ml.matrix.PartitionLocation;
import com.tencent.angel.ps.ParameterServerId;
import com.tencent.angel.ps.server.data.PSLocation;
import com.tencent.angel.psagent.PSAgentContext;
import com.tencent.angel.psagent.matrix.transport.adapter.RowIndex;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PSAgent matrix meta manager
 */
public class PSAgentMatrixMetaManager {
  private static final Log LOG = LogFactory.getLog(PSAgentMatrixMetaManager.class);
  /**
   * Matrix meta manager
   */
  private final MatrixMetaManager matrixMetaManager;

  /**
   * Matrix id -> the partitions of the matrix map
   */
  private final Map<Integer, List<PartitionKey>> matrixIdToPartsMap;

  /**
   * Matrix id -> (row index -> contain this row partitions map) map
   */
  private final Map<Integer, Map<Integer, List<PartitionKey>>> rowIndexToPartsMap;

  public PSAgentMatrixMetaManager() {
    //this.partClockCache = partClockCache;
    this.matrixMetaManager = new MatrixMetaManager();
    this.matrixIdToPartsMap = new ConcurrentHashMap<>();
    this.rowIndexToPartsMap = new ConcurrentHashMap<>();
  }

  /**
   * Add matrices meta
   *
   * @param matrixMetas matrices meta
   */
  public void addMatrices(List<MatrixMeta> matrixMetas) {
    int size = matrixMetas.size();
    for (int i = 0; i < size; i++) {
      addMatrix(matrixMetas.get(i));
    }
  }

  /**
   * Add matrix meta
   *
   * @param matrixMeta matrix meta
   */
  public void addMatrix(MatrixMeta matrixMeta) {
    matrixMetaManager.addMatrix(matrixMeta);
  }

  /**
   * Remove matrix meta
   *
   * @param matrixId matrix id
   */
  public void removeMatrix(int matrixId) {
    matrixMetaManager.removeMatrix(matrixId);
  }

  /**
   * Get the server that hold the partition.
   *
   * @param partitionKey matrix partition key
   * @param sync         true means get from master, false means get from local cache
   * @return ParameterServerId server id
   */
  public List<ParameterServerId> getPss(PartitionKey partitionKey, boolean sync)
    throws ServiceException {
    if (!sync) {
      return getPss(partitionKey);
    } else {
      List<ParameterServerId> psIds = PSAgentContext.get().getMasterClient()
        .getStoredPss(partitionKey.getMatrixId(), partitionKey.getPartitionId());
      matrixMetaManager.setPss(partitionKey.getMatrixId(), partitionKey.getPartitionId(), psIds);
      return psIds;
    }
  }

  /**
   * Get partition location: includes stored pss and the location of the pss
   *
   * @param partitionKey partition information
   * @param sync         true means get from master; false means get from cache
   * @return partition location
   * @throws ServiceException
   */
  public PartitionLocation getPartLocation(PartitionKey partitionKey, boolean sync)
    throws ServiceException {
    if (!sync) {
      return getPartLocation(partitionKey);
    } else {
      return PSAgentContext.get().getMasterClient()
        .getPartLocation(partitionKey.getMatrixId(), partitionKey.getPartitionId());
    }
  }

  /**
   * Get partition location: includes stored pss and the location of the pss
   *
   * @param matrixId matrix id
   * @param partId partition id
   * @param sync         true means get from master; false means get from cache
   * @return partition location
   * @throws ServiceException
   */
  public PartitionLocation getPartLocation(int matrixId, int partId, boolean sync)
      throws ServiceException {
    if (!sync) {
      return getPartLocation(matrixId, partId);
    } else {
      return PSAgentContext.get().getMasterClient()
          .getPartLocation(matrixId, partId);
    }
  }

  /**
   * Get partition location: includes stored pss and the location of the pss
   *
   * @param partitionKey partition information
   * @return partition location
   * @throws ServiceException
   */
  public PartitionLocation getPartLocation(PartitionKey partitionKey) {
    List<ParameterServerId> psIds = getPss(partitionKey);
    if (psIds == null) {
      return new PartitionLocation(new ArrayList<>());
    }

    int size = psIds.size();
    List<PSLocation> psLocs = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      psLocs.add(new PSLocation(psIds.get(i),
        PSAgentContext.get().getLocationManager().getPsLocation(psIds.get(i))));
    }
    return new PartitionLocation(psLocs);
  }

  /**
   * Get the server that hold the partition.
   *
   * @param partitionKey matrix partition key
   * @return ParameterServerId server id
   */
  public List<ParameterServerId> getPss(PartitionKey partitionKey) {
    return matrixMetaManager.getPss(partitionKey.getMatrixId(), partitionKey.getPartitionId());
  }

  /**
   * Get partition location: includes stored pss and the location of the pss
   * TODO: cache
   *
   * @param matrixId partition information
   * @param partId partition id
   * @return partition location
   * @throws ServiceException
   */
  public PartitionLocation getPartLocation(int matrixId, int partId) {
    List<ParameterServerId> psIds = getPss(matrixId, partId);
    if (psIds == null) {
      return new PartitionLocation(new ArrayList<>());
    }

    int size = psIds.size();
    List<PSLocation> psLocs = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      psLocs.add(new PSLocation(psIds.get(i),
          PSAgentContext.get().getLocationManager().getPsLocation(psIds.get(i))));
    }
    return new PartitionLocation(psLocs);
  }

  public List<ParameterServerId> getPss(int matrixId, int partId) {
    return matrixMetaManager.getPss(matrixId, partId);
  }

  /**
   * Get the server that hold the partition.
   *
   * @return ParameterServerId server id
   */
  public List<ParameterServerId> getPss(int matrixId) {
    return matrixMetaManager.getPss(matrixId);
  }

  /**
   * Get list of partitionKeys for matrixId and rowId.
   *
   * @param matrixId, matrixId
   * @param rowIndex, rowId
   * @return list of partitionKeys
   */
  public List<PartitionKey> getPartitions(int matrixId, int rowIndex) {
    Map<Integer, List<PartitionKey>> rowPartKeysCache = rowIndexToPartsMap.get(matrixId);
    if (rowPartKeysCache == null) {
      rowPartKeysCache = new ConcurrentHashMap<>();
      rowIndexToPartsMap.put(matrixId, rowPartKeysCache);
    }
    List<PartitionKey> rowParts = rowPartKeysCache.get(rowIndex);
    if (rowParts == null) {
      rowParts = MatrixMetaUtils.getPartitions(getMatrixMeta(matrixId), rowIndex);

      rowPartKeysCache.put(rowIndex, rowParts);
    }

    return rowParts;
  }


  /**
   * Get list of partitionkeys belong to matrixId.
   *
   * @param matrixId, identity number of matrix requested
   * @return list of partitionKeys
   */
  public List<PartitionKey> getPartitions(int matrixId) {
    List<PartitionKey> partitions = matrixIdToPartsMap.get(matrixId);
    if (partitions == null) {
      partitions = MatrixMetaUtils.getPartitions(getMatrixMeta(matrixId));
      matrixIdToPartsMap.put(matrixId, partitions);
    }

    return partitions;
  }


  /**
   * Get the partitions the rows in.
   *
   * @param matrixId   matrix id
   * @param rowIndexes row indexes
   * @return Map<PartitionKey, List<Integer>> partitions to the rows contained in the partition map
   */
  public Map<PartitionKey, List<Integer>> getPartitionToRowsMap(int matrixId,
    List<Integer> rowIndexes) {
    Map<PartitionKey, List<Integer>> partToRowsMap = new HashMap<PartitionKey, List<Integer>>();

    int rowNum = rowIndexes.size();
    int partNum;
    for (int i = 0; i < rowNum; i++) {
      List<PartitionKey> partKeys = getPartitions(matrixId, rowIndexes.get(i));
      partNum = partKeys.size();
      for (int j = 0; j < partNum; j++) {
        List<Integer> rows = partToRowsMap.get(partKeys.get(j));
        if (rows == null) {
          rows = new ArrayList<>();
          partToRowsMap.put(partKeys.get(j), rows);
        }
        rows.add(rowIndexes.get(i));
      }
    }
    return partToRowsMap;
  }

  /**
   * Get the partitions the rows in.
   *
   * @param matrixId matrix id
   * @param rowIds   row indexes
   * @return Map<PartitionKey, List<Integer>> partitions to the rows contained in the partition map
   */
  public Map<PartitionKey, List<Integer>> getPartitionToRowsMap(int matrixId, int[] rowIds) {
    Map<PartitionKey, List<Integer>> partToRowsMap = new HashMap<PartitionKey, List<Integer>>();

    int rowNum = rowIds.length;
    int partNum;
    for (int i = 0; i < rowNum; i++) {
      List<PartitionKey> partKeys = getPartitions(matrixId, rowIds[i]);
      partNum = partKeys.size();
      for (int j = 0; j < partNum; j++) {
        List<Integer> rows = partToRowsMap.get(partKeys.get(j));
        if (rows == null) {
          rows = new ArrayList<>();
          partToRowsMap.put(partKeys.get(j), rows);
        }
        rows.add(rowIds[i]);
      }
    }
    return partToRowsMap;
  }

  /**
   * Get the partitions the rows in.
   *
   * @param rowIndex    matrix id and row indexes
   * @param batchNumber the split batch size.
   * @return Map<PartitionKey, List<Integer>> partitions to the rows contained in the partition map
   */
  public Map<PartitionKey, List<RowIndex>> getPartitionToRowIndexMap(RowIndex rowIndex,
    int batchNumber) {
    Map<PartitionKey, List<RowIndex>> partToRowIndexMap =
      new TreeMap<PartitionKey, List<RowIndex>>();
    if (rowIndex.getRowIds() == null) {
      return partToRowIndexMap;
    }

    IntOpenHashSet rowIdSet = rowIndex.getRowIds();
    IntOpenHashSet filtedRowIdSet = rowIndex.getFiltedIdSet();
    int[] rowIds = new int[rowIdSet.size() - filtedRowIdSet.size()];
    int count = 0;
    for (int rowId : rowIdSet) {
      if (!filtedRowIdSet.contains(rowId)) {
        rowIds[count++] = rowId;
      }
    }

    Arrays.sort(rowIds);

    int partNum = 0;
    for (int i = 0; i < rowIds.length; i++) {
      List<PartitionKey> partKeys = getPartitions(rowIndex.getMatrixId(), rowIds[i]);
      partNum = partKeys.size();
      for (int j = 0; j < partNum; j++) {
        PartitionKey partitionKey = partKeys.get(j);
        List<RowIndex> indexList = partToRowIndexMap.get(partitionKey);
        if (indexList == null) {
          indexList = new ArrayList<RowIndex>();
          partToRowIndexMap.put(partitionKey, indexList);
          indexList.add(new RowIndex(rowIndex.getMatrixId(), rowIndex));
        }

        RowIndex index = indexList.get(indexList.size() - 1);
        if (index.getRowsNumber() >= batchNumber) {
          index = new RowIndex(rowIndex.getMatrixId(), rowIndex);
          indexList.add(index);
        }
        index.addRowId(rowIds[i]);
      }
    }

    return partToRowIndexMap;
  }

  /**
   * Get the number of the partitions that contains the row.
   *
   * @param matrixId matrix id
   * @param rowIndex row index
   * @return the number of the partitions that contains the row
   */
  public int getRowPartitionSize(int matrixId, int rowIndex) {
    return getPartitions(matrixId, rowIndex).size();
  }

  /**
   * Clear the router table.
   */
  public void clear() {
    matrixIdToPartsMap.clear();
    rowIndexToPartsMap.clear();
    matrixMetaManager.clear();
  }

  /**
   * Get matrix id
   *
   * @param matrixName matrix name
   * @return matrix id
   */
  public int getMatrixId(String matrixName) {
    if (matrixMetaManager.exists(matrixName)) {
      return matrixMetaManager.getMatrixId(matrixName);
    } else {
      synchronized (this) {
        if (!matrixMetaManager.exists(matrixName)) {
          try {
            PSAgentContext.get().getPsAgent().refreshMatrixInfo();
          } catch (Throwable e) {
            LOG.error("get matrix information from master failed. ", e);
          }
        }
        if (matrixMetaManager.exists(matrixName)) {
          return matrixMetaManager.getMatrixId(matrixName);
        } else {
          return -1;
        }
      }
    }
  }

  /**
   * Get matrix meta
   *
   * @param matrixId matrix id
   * @return matrix meta
   */
  public MatrixMeta getMatrixMeta(int matrixId) {
    return matrixMetaManager.getMatrixMeta(matrixId);
  }

  /**
   * Get matrix meta
   *
   * @param matrixName matrix name
   * @return matrix meta
   */
  public MatrixMeta getMatrixMeta(String matrixName) {
    return matrixMetaManager.getMatrixMeta(matrixName);
  }

  /**
   * Get the master ps of a partition
   *
   * @param partKey partition key
   * @return the master ps of a partition
   */
  public ParameterServerId getMasterPS(PartitionKey partKey) {
    return matrixMetaManager.getMasterPs(partKey.getMatrixId(), partKey.getPartitionId());
  }

  /**
   * Get all matrices meta
   *
   * @return all matrices meta
   */
  public Map<Integer, MatrixMeta> getMatrixMetas() {
    return matrixMetaManager.getMatrixMetas();
  }

  /**
   * Is a matrix exist
   *
   * @param matrixId matrix id
   * @return true means exist
   */
  public boolean exist(int matrixId) {
    if (!matrixMetaManager.exists(matrixId)) {
      synchronized (this) {
        if (!matrixMetaManager.exists(matrixId)) {
          try {
            PSAgentContext.get().getPsAgent().refreshMatrixInfo();
          } catch (Throwable e) {
            LOG.error("get matrix information from master failed. ", e);
          }
        }
      }
    }
    return matrixMetaManager.exists(matrixId);
  }

  public ParameterServerId getMasterPS(int matrixId, int partId) {
    return matrixMetaManager.getMasterPs(matrixId, partId);
  }
}
