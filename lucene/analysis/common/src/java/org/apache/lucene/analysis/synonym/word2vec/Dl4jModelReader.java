/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.analysis.synonym.word2vec;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.TermAndVector;

/**
 * Dl4jModelReader reads the file generated by the library Deeplearning4j and provide a
 * Word2VecModel with normalized vectors
 *
 * <p>Dl4j Word2Vec documentation:
 * https://deeplearning4j.konduit.ai/v/en-1.0.0-beta7/language-processing/word2vec Example to
 * generate a model using dl4j:
 * https://github.com/eclipse/deeplearning4j-examples/blob/master/dl4j-examples/src/main/java/org/deeplearning4j/examples/advanced/modelling/embeddingsfromcorpus/word2vec/Word2VecRawTextExample.java
 *
 * @lucene.experimental
 */
public class Dl4jModelReader implements Closeable {

  private static final String MODEL_FILE_NAME_PREFIX = "syn0";

  private final ZipInputStream word2VecModelZipFile;

  public Dl4jModelReader(InputStream stream) {
    this.word2VecModelZipFile = new ZipInputStream(new BufferedInputStream(stream));
  }

  public Word2VecModel read() throws IOException {

    ZipEntry entry;
    while ((entry = word2VecModelZipFile.getNextEntry()) != null) {
      String fileName = entry.getName();
      if (fileName.startsWith(MODEL_FILE_NAME_PREFIX)) {
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(word2VecModelZipFile, StandardCharsets.UTF_8));

        String header = reader.readLine();
        String[] headerValues = header.split(" ");
        int dictionarySize = Integer.parseInt(headerValues[0]);
        int vectorDimension = Integer.parseInt(headerValues[1]);

        Word2VecModel model = new Word2VecModel(dictionarySize, vectorDimension);
        String line = reader.readLine();
        boolean isTermB64Encoded = false;
        if (line != null) {
          String[] tokens = line.split(" ");
          isTermB64Encoded =
              tokens[0].substring(0, 3).toLowerCase(Locale.ROOT).compareTo("b64") == 0;
          model.addTermAndVector(extractTermAndVector(tokens, vectorDimension, isTermB64Encoded));
        }
        while ((line = reader.readLine()) != null) {
          String[] tokens = line.split(" ");
          model.addTermAndVector(extractTermAndVector(tokens, vectorDimension, isTermB64Encoded));
        }
        return model;
      }
    }
    throw new IllegalArgumentException(
        "Cannot read Dl4j word2vec model - '"
            + MODEL_FILE_NAME_PREFIX
            + "' file is missing in the zip. '"
            + MODEL_FILE_NAME_PREFIX
            + "' is a mandatory file containing the mapping between terms and vectors generated by the DL4j library.");
  }

  private static TermAndVector extractTermAndVector(
      String[] tokens, int vectorDimension, boolean isTermB64Encoded) {
    BytesRef term = isTermB64Encoded ? decodeB64Term(tokens[0]) : new BytesRef((tokens[0]));

    float[] vector = new float[tokens.length - 1];

    if (vectorDimension != vector.length) {
      throw new RuntimeException(
          String.format(
              Locale.ROOT,
              "Word2Vec model file corrupted. "
                  + "Declared vectors of size %d but found vector of size %d for word %s (%s)",
              vectorDimension,
              vector.length,
              tokens[0],
              term.utf8ToString()));
    }

    for (int i = 1; i < tokens.length; i++) {
      vector[i - 1] = Float.parseFloat(tokens[i]);
    }
    return new TermAndVector(term, vector);
  }

  static BytesRef decodeB64Term(String term) {
    byte[] buffer = Base64.getDecoder().decode(term.substring(4));
    return new BytesRef(buffer, 0, buffer.length);
  }

  @Override
  public void close() throws IOException {
    word2VecModelZipFile.close();
  }
}
